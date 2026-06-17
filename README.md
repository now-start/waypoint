# Waypoint

Waypoint는 창원시 버스 TAGO 데이터를 수집해 노선, 정류장, 차량 위치, 도착정보, 이상징후, AI 운영 브리핑을 한 화면에서 확인하는 Spring Boot 기반 관제 콘솔입니다.

## 주요 기능

- TAGO 기준 데이터, 차량 위치, 도착정보 수집
- 좌표 기반 노선 그래프와 차량 위치 표시
- 노선 필터 다중 선택, 지도 확대/축소, 차량 진행 목록
- 위치 갱신 지연과 차량 간격 기반 이상징후 표시
- 최근 수집 실행 이력과 실패/부분 성공 메시지 확인
- Ollama 또는 OpenAI 기반 운영 브리핑 초안 생성
- 상세 모달을 통한 노선, 정류소, 노선-정류소, 위치/도착 스냅샷 조회

## 기술 스택

- Java 25
- Spring Boot 4.1.0
- Spring Cloud 2025.1.2
- Spring AI 2.0.0
- Spring Data JPA
- H2 local profile, MariaDB runtime target
- Bootstrap WebJar 5.3.8
- OpenTelemetry instrumentation

## 로컬 실행

```bash
export TAGO_SERVICE_KEY="<encoded-service-key>"
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

대시보드는 기본적으로 [http://localhost:18080](http://localhost:18080) 에서 확인합니다.

로컬 프로필은 다음 설정을 사용합니다.

- H2 in-memory DB: `jdbc:h2:mem:localdb;MODE=MariaDB`
- H2 Console: `/h2-console`
- TAGO city code: `38010`
- Config Server, Eureka 비활성화
- OpenTelemetry SDK 비활성화

TAGO 인증키가 비어 있으면 외부 API 호출은 실패하지만, 애플리케이션과 화면 자체는 기동됩니다.

## AI 브리핑 모델

기본값은 로컬 Ollama입니다.

```bash
WAYPOINT_AI_CHAT_PROVIDER=ollama SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

OpenAI 크레딧을 사용하려면 OpenAI API 키를 런타임 환경에 주입하고 chat provider를 `openai`로 바꿉니다.

```bash
WAYPOINT_AI_CHAT_PROVIDER=openai OPENAI_API_KEY="<project-api-key>" SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

OpenAI 모델 기본값은 `gpt-4o-mini`입니다. 필요하면 `OPENAI_CHAT_MODEL`로 변경합니다.
`docker compose up app`도 같은 환경변수를 읽습니다.

## 주요 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/collections/status` | 수집 상태와 누적 건수 |
| `GET` | `/api/collections/runs?limit=20` | 최근 수집 실행 |
| `POST` | `/api/collections/reference-data` | 기준 데이터 수집 |
| `POST` | `/api/collections/locations` | 차량 위치 수집 |
| `POST` | `/api/collections/arrivals` | 도착정보 수집 |
| `GET` | `/api/operations/map` | 노선 그래프와 차량 위치 |
| `GET` | `/api/anomalies` | 운영 이상징후 |
| `POST` | `/api/briefings/operations` | AI 운영 브리핑 생성 |
| `GET` | `/api/details/{type}` | 상세 데이터 페이지 조회 |

`type`은 `routes`, `stops`, `route-stops`, `locations`, `arrivals`를 사용합니다.

## 수집 주기

로컬 기본값은 `src/main/resources/application-local.yaml`에 있습니다.

- 기준 데이터: 24시간 주기
- 위치: 1분 주기
- 도착정보: 10분 주기
- 위치 갱신 이상징후 기준: 5분

운영 프로필은 `spring.config.import=optional:configserver:https://spring.nowstart.org/config`를 통해 Config Server 값을 우선 사용합니다.

## 검증

```bash
./gradlew test
node --check src/main/resources/static/app.js
git diff --check
```

주요 TAGO API 수동 확인은 `docs/tago-api.http`를 사용합니다.

## 이미지 빌드

```bash
./gradlew bootBuildImage --imageName waypoint:0.0.6
docker compose up app
```

관측성 스택까지 함께 올릴 때는 다음 명령을 사용합니다.

```bash
docker compose --profile observability up
```

Grafana는 `http://localhost:3000`에서 확인합니다.

## 문서

- `docs/prd.md`: 제품 요구사항
- `docs/architecture.md`: 아키텍처 원칙
- `docs/data-model.md`: 데이터 모델
- `docs/tago-api.md`: TAGO 연동 명세
- `docs/screen-design.md`: 화면 설계
- `docs/ai-briefing.md`: AI 브리핑 설계
