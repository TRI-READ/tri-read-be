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
docker compose -f compose.db.yaml up -d
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
gradle test
gradle bootRun
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

## Deployment Draft

```powershell
cd deploy
Copy-Item .env.example .env
docker compose up --build
```

Only Caddy should expose ports 80 and 443. Spring Boot and PostgreSQL stay inside the Docker network.
