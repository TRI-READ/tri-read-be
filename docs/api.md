# API 명세

모든 API는 `/api`를 기준 경로로 사용합니다. 로그인 성공 후 서버 세션 쿠키를 사용하며, 상태를 변경하는 요청은 먼저 `GET /api/csrf`로 받은 CSRF 토큰을 전송해야 합니다.

## 공통 응답

- `200`, `201`, `204`: 정상 처리
- `400`: 요청 값 오류
- `401`: 로그인 필요
- `403`: 권한 부족 또는 CSRF 검증 실패
- `404`: 대상 데이터 없음
- `409`: 현재 상태에서 처리할 수 없는 요청
- `500`: 서버 내부 오류

오류 응답은 `code`, `message`를 사용해 클라이언트가 빈 상태와 장애 상태를 구분할 수 있도록 합니다.

## 사용자 API

| 기능 | 메서드 | 경로 |
| --- | --- | --- |
| 회원가입 | POST | `/api/auth/signup` |
| 로그인 | POST | `/api/auth/login` |
| 로그아웃 | POST | `/api/auth/logout` |
| 내 정보 | GET | `/api/auth/me` |
| 오늘 퀴즈 | POST | `/api/quizzes/today` |
| 남은 보너스 지문 | GET | `/api/quizzes/bonus` |
| 답안 제출 | POST | `/api/quizzes/{quizSetId}/attempts` |
| 오답 목록 | GET | `/api/reviews?status=OPEN` |
| 오답 상세 | GET | `/api/reviews/{reviewId}` |
| 복습 상태 변경 | PATCH | `/api/reviews/{reviewId}` |
| 학습 기록 | GET | `/api/orbit?period=WEEK&anchor=YYYY-MM-DD` |
| 연속 학습 | GET | `/api/orbit/streak` |

답안 제출은 한 지문의 세 문제에 대한 답을 모두 포함해야 합니다. 학습일에 배정된 세트의 첫 제출은 `PRIMARY`, 같은 세트의 추가 지문은 `BONUS`로 기록됩니다. 과거 세트는 `PRIMARY`가 완료된 경우에만 남은 지문을 `BONUS`로 제출할 수 있습니다.

## 관리자 API

관리자 API는 로그인한 `ADMIN` 사용자만 접근할 수 있습니다. 일반 사용자는 `403`, 비로그인 사용자는 `401`을 받습니다.

| 기능 | 메서드 | 경로 |
| --- | --- | --- |
| 운영 현황 | GET | `/api/admin/operations/summary` |
| 생성 기록 | GET | `/api/admin/quiz-generations` |
| 퀴즈 생성 | POST | `/api/admin/quiz-generations` |
| 생성 재시도 | POST | `/api/admin/quiz-generations/{id}/retry` |
| 퀴즈 목록 | GET | `/api/admin/quizzes` |
| 퀴즈 발행 | POST | `/api/admin/quizzes/{id}/publish` |
| 퀴즈 일괄 발행 | POST | `/api/admin/quizzes/bulk-publish` |
| 퀴즈 일괄 삭제 | DELETE | `/api/admin/quizzes/bulk` |
| 품질 현황 | GET | `/api/admin/quiz-quality` |
| 사용자 관리 | GET | `/api/admin/users` |
| 감사 로그 | GET | `/api/admin/audit-logs` |

## 상태 흐름

```text
생성 요청 -> 서버 검증 -> REVIEWED -> 관리자 확인 -> PUBLISHED -> 사용자 배정
                    \-> FAILED -> 원인 확인 -> 재시도
```

실제 요청 필드와 응답 구조는 컨트롤러 DTO와 자동화 테스트를 단일 기준으로 삼습니다.
