# TRI:READ Backend

Spring Boot REST API for TRI:READ, a weekday reading quiz service.

## Stack

- Java 21
- Spring Boot 4.1
- Gradle
- MyBatis XML mapper
- PostgreSQL
- Flyway
- Spring Security session + CSRF
- Docker Compose deployment with PostgreSQL and Caddy

## Domain Baseline

- One daily quiz set has 3 passages.
- Each passage has 3 multiple-choice questions.
- A completed attempt stores 1 `quiz_attempts` row and 9 `attempt_answers` rows.
- Users can join multiple study groups.
- Quiz attempts are stored per user and quiz set, not per group.
- Group leaderboards are derived by filtering attempts through `group_members`.
- Streaks are based on weekdays only. Weekends do not break a streak.

## Run Locally

Gradle and PostgreSQL are required locally. The easiest local setup is to run only
PostgreSQL with Docker Compose and run the Spring Boot app from Gradle or an IDE.

```powershell
docker compose -p tri-read-dev -f compose.db.yaml up -d
```

The default development connection is:

```text
url=jdbc:postgresql://localhost:15432/tri_read
username=tri_read_app
password=tri_read_dev
```

Flyway runs automatically when the app starts and creates the core schema from
`src/main/resources/db/migration`.

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

Load the handwritten development quiz for the current date after PostgreSQL is
running. This script is separate from Flyway and skips insertion when a published
quiz already exists for today.

```powershell
docker cp scripts/seed-dev-quiz.sql tri-read-postgres:/tmp/seed-dev-quiz.sql
docker exec tri-read-postgres psql -v ON_ERROR_STOP=1 `
    -U tri_read_app -d tri_read -f /tmp/seed-dev-quiz.sql
```

Override the connection with environment variables when needed:

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:15432/tri_read"
$env:POSTGRES_USER="tri_read_app"
$env:POSTGRES_PASSWORD="tri_read_dev"
```

## Authentication API

The browser first requests `GET /api/csrf`, then sends the returned header and
cookie with state-changing requests.

```text
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

Signup and login create a server-side session. PINs are stored only as BCrypt
hashes.

## Daily Quiz API

Both endpoints require the authenticated session and the submission endpoint
also requires the CSRF header.

```text
GET  /api/quizzes/today
POST /api/quizzes/{quizSetId}/attempts
```

The submission must contain exactly one answer for each of the 9 questions.
Scoring, attempt persistence, and pending wrong-answer reviews are committed in
one transaction.

## Study Group API

All endpoints require the authenticated session. State-changing endpoints also
require the CSRF header.

```text
POST /api/groups
GET  /api/groups/my
GET  /api/groups/{groupId}
POST /api/groups/join
POST /api/groups/{groupId}/invites
GET  /api/groups/{groupId}/activity
```

Creating a group also creates its owner membership and an initial invite code.
Only the invite-code hash is stored. Renewing an invite is owner-only and
disables the group's previous active codes.

Weekly group activity is calculated without a ranking table. The activity score
is `completed quizzes * 10 + correct answers + recovered reviews * 2`.
Weekend attempts fill the earliest missing weekday in the same week. They do
not create extra required days after all five weekdays have been completed.

## Answer Review API

Review list filters are `OPEN`, `RECOVERED`, and `ALL`. Updating a review to
`RECOVERED` records the review time and increases its retry count.

```text
GET   /api/reviews?status=OPEN
GET   /api/reviews/{reviewId}
PATCH /api/reviews/{reviewId}
```

A perfect 9/9 attempt has no answer reviews and its daily orbit is immediately
fully lit. Otherwise, the daily orbit becomes fully lit when all wrong answers
from that attempt are recovered.

## Study Orbit API

```text
GET /api/orbit?period=WEEK&anchor=2026-07-08
GET /api/orbit?period=MONTH&anchor=2026-07-08
```

Orbit history is derived from quiz attempts and answer reviews, so it does not
need a separate history table. The required orbit has five weekday slots, and a
weekend attempt fills the earliest empty slot in that week. A perfect attempt is
100% lit immediately; otherwise brightness is the recovered wrong-answer ratio.

## Admin Quiz API

Admin accounts can create a complete quiz as a draft, inspect it, and publish it.
The server enforces exactly 3 passages, 3 questions per passage, and 4 options
per question. Only one published quiz is allowed for a challenge date.

```text
GET  /api/admin/quizzes
GET  /api/admin/quizzes/{quizSetId}
POST /api/admin/quizzes
PUT  /api/admin/quizzes/{quizSetId}
DELETE /api/admin/quizzes/{quizSetId}
POST /api/admin/quizzes/{quizSetId}/publish
```

Drafts and AI-reviewed quizzes can be edited or deleted. Editing a reviewed quiz
returns it to draft status and invalidates its automated validation log. Published quizzes are immutable because
attempt and answer history may already reference their content.

## Automated Quiz Generation

Generation uses Gemini structured outputs, deterministic server rules, and an
independent AI validation pass. A quiz is saved as `REVIEWED` only
after both validators score at least 90. Failed generations retry up to three
times, and auto-publishing is disabled by default so an administrator can inspect
the result.

```text
POST /api/admin/quiz-generations
GET  /api/admin/quiz-generations
GET  /api/admin/quiz-generations/{generationLogId}
```

Copy `.env.example` to your local environment configuration and provide the
Gemini API key as an environment variable. Never commit a real key. OCI keeps
this value in the server-side `.env` file with restricted permissions.

## Deployment Draft

```powershell
cd deploy
Copy-Item .env.example .env
docker compose up --build
```

Only Caddy should expose ports 80 and 443. Spring Boot and PostgreSQL stay inside the Docker network.
