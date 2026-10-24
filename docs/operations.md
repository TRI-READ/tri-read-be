# 운영 대응 안내

## 기본 확인 순서

1. `https://tri-read.duckdns.org/api/health`가 정상인지 확인합니다.
2. GitHub Actions의 최근 배포 및 `Production smoke` 결과를 확인합니다.
3. 관리자 화면의 운영 현황에서 DB, Gemini 호출, 퀴즈 재고, 최근 장애를 확인합니다.
4. OCI에서 `docker compose ps`와 백엔드 로그를 확인합니다.

## 주요 장애별 대응

### 웹은 열리지만 API가 실패할 때

- Caddy가 `/api/*` 요청을 백엔드로 전달하는지 확인합니다.
- 백엔드 컨테이너 상태와 `/api/health` 응답을 확인합니다.
- DB 컨테이너가 정상인지 확인합니다.

### 오늘 퀴즈가 없을 때

- 관리자 운영 현황의 `사용자별 실제 재고`를 확인합니다. 날짜별 발행 현황은 생산 참고 지표이며 개인 재고가 아닙니다.
- 개인 재고는 현재 배정 가능한 미배정 발행 세트와 오늘·이월 가능한 미시작 배정 세트를 더한 값입니다. 이미 기본 학습을 완료한 세트의 남은 보너스는 포함하지 않습니다.
- `PUBLISHED`, 사용 가능일 도래, 배정 중단되지 않은 세트만 새 배정 재고에 포함합니다. 초안·검수 완료·미래 사용 예정 콘텐츠는 별도로 표시합니다.
- 활성 사용자는 최근 30일 내 로그인 또는 풀이 기록이 있는 활성 계정이며 관리자도 포함합니다. 정지 계정은 제외하고, 활성 사용자가 없으면 재고 부족 판정을 보류합니다.
- 활성 사용자 전체의 최저 재고가 14세트 미만이면 주의, 7세트 미만이면 긴급 경보를 관리자 화면에 표시합니다. 상세 목록은 재고가 적은 순서로 최대 20명이고, 최저값은 전체 활성 사용자로 계산합니다. 단위는 지문 수나 일수가 아닌 **세트 수**입니다.
- 검수·발행 대기 경보는 사용 가능일이 되었고 생성 후 48시간 이상 미발행인 초안·검수 완료 세트입니다. 퀴즈 관리에서 검수·발행하거나 불필요한 초안을 정리합니다. 미래 사용 예정·배정 중단 콘텐츠는 장기 대기에서 제외합니다.
- 기준은 `CONTENT_INVENTORY_ACTIVE_WINDOW_DAYS`, `CONTENT_INVENTORY_WARNING_THRESHOLD`, `CONTENT_INVENTORY_CRITICAL_THRESHOLD`, `CONTENT_INVENTORY_REVIEW_WAIT_HOURS`로 조정합니다. 이 경보는 운영 화면 조회 시 계산되며 Discord 자동 전송이나 생성 스케줄 변경은 하지 않습니다.
- 생성 로그가 `FAILED`이면 상세 오류를 확인한 뒤 재시도합니다.
- 모델 종료, 호출 한도, 검증 실패를 구분해 처리합니다.

### Gemini 생성이 반복 실패할 때

- 관리자 화면에서 호출 한도 오류가 보이지만 Google AI Studio에 요청 기록이 없다면 `ai_api_calls`를 먼저 확인합니다. `generation_logs` 개수는 일일 호출 한도에 영향을 주지 않습니다. 같은 현상이 계속되면 운영 서버가 이전 버전을 실행 중인지 배포 상태를 확인합니다.

- `404 NOT_FOUND`이면 설정 모델이 폐기됐거나 현재 프로젝트에서 사용할 수 없는지 확인하고 지원 모델로 변경합니다.
- `429 RESOURCE_EXHAUSTED`이면 API 키 존재 여부가 아니라 해당 모델 또는 기능의 공급자 할당량을 확인합니다.
- 일반 Gemini 모델 호출량과 Google Search Grounding 할당량은 별개입니다. AI Studio에서 일반 모델 한도가 남아 있어도 Grounding 호출은 실패할 수 있습니다.
- 관리자 화면의 `오늘 Gemini 호출` 수치는 서비스 내부 일일 호출 방어선이며 Google의 실제 할당량이 아닙니다.
- Search Grounding은 기본 비활성화입니다. 프로젝트와 요금제가 지원할 때만 `QUIZ_SOURCE_GROUNDING_ENABLED=true`로 켭니다.
- `429`를 짧은 간격으로 무조건 재시도하지 않습니다. Grounding을 끈 상태에서 일반 생성이 정상인지 먼저 확인합니다.
- 실패 원인이 품질 검증이면 지문 생성 프롬프트와 검증 상세를 확인합니다.
- API 키나 실제 비밀값은 로그, 이슈, 문서에 남기지 않습니다.

참고 문서:

- [Gemini API 가격 및 무료 등급](https://ai.google.dev/gemini-api/docs/pricing)
- [Gemini API 호출 한도](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Gemini API 오류 코드](https://ai.google.dev/gemini-api/docs/api-errors)

### Windows에서 Gradle 테스트 클래스를 찾지 못할 때

- 저장소 경로에 한글이 있으면 Gradle 테스트 워커의 클래스패스가 깨져 `ClassNotFoundException`이 발생할 수 있습니다.
- 컴파일된 테스트 클래스가 존재하는데도 테스트 실행 직후 클래스를 찾지 못한다면 코드 오류보다 경로 인코딩을 먼저 확인합니다.
- 이 프로젝트는 `TRI_READ_BUILD_DIR` 환경변수로 빌드 산출물 경로를 분리할 수 있습니다. 영문 경로를 지정한 뒤 다시 실행합니다.

```powershell
$env:TRI_READ_BUILD_DIR='C:\Users\admin\Documents\tri-read-build'
.\gradlew.bat --no-daemon clean test
```

### 배포 후 장애가 발생했을 때

- 직전 정상 이미지나 `main` 커밋으로 되돌립니다.
- DB 스키마 변경이 포함됐다면 단순 코드 롤백 전에 호환성을 확인합니다.
- 장애 원인과 조치 결과를 운영 이벤트와 변경 이력에 기록합니다.

## 백업과 복원

- 운영 DB는 GitHub Actions에서 암호화해 백업합니다.
- 복원 전 현재 DB를 한 번 더 백업합니다.
- 복원은 별도 검증 DB에서 먼저 확인한 뒤 운영에 적용합니다.
- 복원 후 health, 로그인, 오늘 퀴즈, 관리자 권한 경계를 확인합니다.

## 운영 스모크 기준

- 웹 첫 화면 응답 성공
- `/api/health` 응답 성공
- 비로그인 사용자의 관리자 API 접근이 `401`
- 운영 컨테이너가 모두 정상 상태

민감한 키, 비밀번호, SSH 개인키는 GitHub Secrets와 운영 서버의 권한 제한 파일에서만 관리합니다.
