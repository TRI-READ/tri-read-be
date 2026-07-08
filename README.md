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

Gradle is required locally.

```powershell
gradle test
gradle bootRun
```

The local app expects PostgreSQL at `localhost:5432` by default. Override with environment variables or use the deployment compose file after Docker is available.

## Deployment Draft

```powershell
cd deploy
Copy-Item .env.example .env
docker compose up --build
```

Only Caddy should expose ports 80 and 443. Spring Boot and PostgreSQL stay inside the Docker network.

