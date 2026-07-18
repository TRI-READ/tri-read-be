# TRI:READ 백엔드

TRI:READ는 이동 시간에도 부담 없이 풀 수 있는 고3 수준의 비문학 독해 서비스입니다. 이 저장소는 인증, 퀴즈, 오답 복습, 학습 기록, 그룹 기능과 AI 문제 생성을 담당하는 Spring Boot API 서버입니다.

## 현재 학습 정책

- 평일마다 서로 다른 영역의 지문 3개와 지문별 객관식 3문제를 제공합니다.
- 사용자는 원하는 지문 하나를 골라 3문제를 풀면 오늘의 필수 학습을 완료합니다.
- 필수 학습 완료 후 남은 지문 2개는 보너스로 선택해 풀 수 있습니다.
- 한 사용자에게 배정된 날짜별 퀴즈 세트는 새로고침하거나 다시 로그인해도 바뀌지 않습니다.
- 주말 학습은 강제가 아니며, 해당 주의 비어 있는 평일 기록을 가장 이른 날짜부터 채웁니다.
- 정답만 맞힌 지문은 즉시 복습 완료로 기록되고, 오답이 있으면 모든 오답을 복습한 뒤 완료됩니다.

## 기술 스택

- Java 21
- Spring Boot 4.1
- Gradle
- Spring Security 세션 인증 및 CSRF
- MyBatis XML Mapper
- PostgreSQL
- Flyway
- Docker Compose
- Caddy
- Gemini API
- GitHub Actions
- OCI Compute

## 주요 기능

- PIN 기반 회원가입, 로그인, 로그아웃
- 오늘의 퀴즈 배정 및 필수/보너스 풀이
- 채점, 풀이 기록과 오답 복습
- 주간/월간 학습 기록 및 연속 학습일 계산
- 여러 학습 그룹 가입, 초대 코드와 그룹 활동 조회
- 관리자 퀴즈 작성, 수정, 검수와 발행
- Gemini 기반 문제 생성 및 자동 검증
- OCI 배포와 DuckDNS IP 갱신

## 로컬 실행

로컬 PostgreSQL은 Docker Compose로 실행하고 애플리케이션은 Gradle로 실행하는 구성을 권장합니다.

```powershell
docker compose -p tri-read-dev -f compose.db.yaml up -d
```

기본 개발용 DB 접속 정보는 다음과 같습니다.

```text
URL: jdbc:postgresql://localhost:15432/tri_read
사용자: tri_read_app
비밀번호: tri_read_dev
```

애플리케이션이 시작될 때 Flyway가 `src/main/resources/db/migration`의 스키마를 자동 적용합니다.

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

### Gemini 키 설정

`config/application-secret.example.yml`을 `config/application-secret.yml`로 복사하고 아래 항목에 로컬 Gemini API 키를 넣습니다.

```yaml
app:
  quiz-generation:
    gemini:
      api-key: "발급받은 키"
```

실제 `application-secret.yml`은 Git에서 제외되며 JAR에도 포함되지 않습니다. 키가 들어 있는 파일은 커밋하지 않습니다.

## 주요 API

브라우저는 상태 변경 요청 전에 `GET /api/csrf`로 CSRF 토큰을 받습니다. 인증이 필요한 API는 서버 세션 쿠키를 사용합니다.

```text
# 인증
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me

# 퀴즈
GET  /api/quizzes/today
POST /api/quizzes/{quizSetId}/attempts

# 오답 복습
GET   /api/reviews?status=OPEN
GET   /api/reviews/{reviewId}
PATCH /api/reviews/{reviewId}

# 학습 기록
GET /api/orbit?period=WEEK&anchor=2026-07-18
GET /api/orbit?period=MONTH&anchor=2026-07-18
GET /api/orbit/streak

# 그룹
POST /api/groups
GET  /api/groups/my
GET  /api/groups/{groupId}
POST /api/groups/join
POST /api/groups/{groupId}/invites
GET  /api/groups/{groupId}/activity
```

퀴즈 제출 요청은 한 지문에 속한 정확히 3개의 답을 포함해야 합니다. 첫 제출은 `PRIMARY`, 이후 다른 지문의 제출은 `BONUS`로 저장됩니다.

## 관리자와 문제 생성

관리자는 퀴즈 초안을 작성하거나 Gemini로 생성한 문제를 검수한 뒤 발행할 수 있습니다. 서버는 퀴즈 한 세트가 지문 3개, 지문별 문제 3개, 문제별 선택지 4개인지 검증합니다.

```text
GET    /api/admin/quizzes
GET    /api/admin/quizzes/{quizSetId}
POST   /api/admin/quizzes
PUT    /api/admin/quizzes/{quizSetId}
DELETE /api/admin/quizzes/{quizSetId}
POST   /api/admin/quizzes/{quizSetId}/publish

POST /api/admin/quiz-generations
GET  /api/admin/quiz-generations
GET  /api/admin/quiz-generations/{generationLogId}
```

AI 생성 결과는 서버 규칙 검증과 별도의 AI 검증을 모두 통과해야 `REVIEWED` 상태가 됩니다. 자동 발행은 기본적으로 꺼져 있어 관리자가 최종 내용을 확인할 수 있습니다.

## 배포

운영 환경은 OCI 인스턴스에서 Docker Compose로 Spring Boot, PostgreSQL, Caddy를 실행합니다. 외부에는 Caddy의 80/443 포트만 공개하고 애플리케이션과 DB는 Docker 내부 네트워크에 둡니다.

```powershell
cd deploy
Copy-Item .env.example .env
docker compose up --build -d
```

`dev` 브랜치에서 개발하고 CI를 통과한 변경을 PR로 `main`에 승격합니다. `main`은 배포 기준 브랜치입니다.

## 저장소

- 프론트엔드: https://github.com/TRI-READ/tri-read-fe
- 백엔드: https://github.com/TRI-READ/tri-read-be
