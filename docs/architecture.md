# 클린 아키텍처

## 1. 결정

Waypoint는 Spring Boot 기반 클린 아키텍처로 구현한다.

참고 구조는 `lotto-service`의 `adapter.in`, `adapter.out`, `application.port.in`, `application.port.out`, `application.service`, `domain`, `config` 패키지 구성을 따른다.

MVP 구현에서도 단순 `controller`, `service`, `repository`, `data` 중심 패키지 구조를 새로 만들지 않는다. 외부 시스템과 프레임워크 세부사항은 adapter 계층에 두고, 수집/계산/브리핑 유스케이스는 application 계층에 둔다.

## 2. 의존성 방향

의존성 방향은 다음으로 제한한다.

```text
adapter -> application -> domain
config -> adapter/application
```

규칙:

- `domain`은 Spring, JPA, HTTP, Ollama, TAGO를 모른다.
- `application.port`는 인터페이스와 command/result 값만 둔다.
- `application.service`는 유스케이스 흐름, 트랜잭션 경계, 포트 호출을 담당한다.
- `adapter.in`은 web, scheduler, startup 같은 입력 어댑터를 둔다.
- `adapter.out`은 persistence, TAGO API, Ollama 같은 출력 어댑터를 둔다.
- `config`는 DI, 설정 properties, Swagger 같은 프레임워크 구성을 둔다.

## 3. 패키지 구조

```text
org.nowstart.waypoint
├─ Application
├─ config
│  ├─ SwaggerConfig
│  ├─ TagoProperties
│  ├─ OllamaProperties
│  └─ AnomalyProperties
├─ domain
│  ├─ model
│  │  ├─ BusRoute
│  │  ├─ BusStop
│  │  ├─ RouteStop
│  │  ├─ BusLocationSnapshot
│  │  ├─ BusArrivalSnapshot
│  │  ├─ CollectionRun
│  │  ├─ OperationAnomaly
│  │  └─ AiBriefing
│  ├─ type
│  │  ├─ CollectionStatus
│  │  ├─ AnomalyType
│  │  └─ Severity
│  └─ exception
├─ application
│  ├─ port
│  │  ├─ in
│  │  │  ├─ CollectReferenceDataUseCase
│  │  │  ├─ CollectBusLocationUseCase
│  │  │  ├─ QueryDashboardUseCase
│  │  │  └─ GenerateAiBriefingUseCase
│  │  └─ out
│  │     ├─ LoadTagoRoutePort
│  │     ├─ LoadTagoLocationPort
│  │     ├─ LoadTagoArrivalPort
│  │     ├─ SaveTransitDataPort
│  │     ├─ LoadTransitDataPort
│  │     └─ GenerateBriefingPort
│  └─ service
│     ├─ ReferenceDataCollectionInteractor
│     ├─ BusLocationCollectionInteractor
│     ├─ DashboardQueryInteractor
│     └─ AiBriefingInteractor
└─ adapter
   ├─ in
   │  ├─ web
   │  │  ├─ DashboardController
   │  │  ├─ CollectionController
   │  │  └─ response
   │  ├─ scheduler
   │  │  └─ TagoCollectionScheduler
   │  └─ startup
   │     └─ ReferenceDataInitializer
   └─ out
      ├─ persistence
      │  ├─ entity
      │  ├─ repository
      │  ├─ mapper
      │  └─ TransitPersistenceAdapter
      ├─ tago
      │  ├─ TagoClient
      │  ├─ TagoRouteAdapter
      │  ├─ TagoLocationAdapter
      │  └─ TagoArrivalAdapter
      └─ ollama
         └─ OllamaBriefingAdapter
```

## 4. MVP-0 데이터 확보 흐름

```text
TagoCollectionScheduler 또는 CollectionController
  -> CollectReferenceDataUseCase / CollectBusLocationUseCase
    <- ReferenceDataCollectionInteractor / BusLocationCollectionInteractor
      -> LoadTagoRoutePort / LoadTagoLocationPort
        <- adapter.out.tago
      -> SaveTransitDataPort
        <- adapter.out.persistence
```

MVP-0은 이상징후 임계값을 확정하지 않는다. 먼저 TAGO 데이터를 저장하고 수집 성공/실패, 위치정보 분포, 차량 간격 분포를 관측한다.

## 5. MVP-1 운영 콘솔 흐름

```text
DashboardController
  -> QueryDashboardUseCase
    <- DashboardQueryInteractor
      -> LoadTransitDataPort
        <- adapter.out.persistence

DashboardController
  -> GenerateAiBriefingUseCase
    <- AiBriefingInteractor
      -> LoadTransitDataPort
      -> GenerateBriefingPort
        <- adapter.out.ollama
      -> SaveTransitDataPort
```

MVP-1은 운영현황, 이상징후 후보, AI 브리핑을 통합 대시보드에 표시한다.

## 6. Spring Annotation 규칙

| Package | 허용 | 금지 |
| --- | --- | --- |
| `config` | `@Configuration`, `@Bean`, `@ConfigurationProperties`, `@Validated` | `@RestController`, `@Entity` |
| `domain.*` | 없음 | 모든 Spring annotation, JPA annotation |
| `application.port.*` | 없음 | 모든 Spring annotation |
| `application.service` | `@Service` | `@RestController`, `@Repository`, JPA annotation |
| `adapter.in.web` | `@Controller`, `@RestController`, `@RequestMapping` | `@Service`, `@Repository`, JPA annotation |
| `adapter.in.scheduler` | `@Component`, `@Scheduled` | `@RestController`, `@Repository`, JPA annotation |
| `adapter.in.startup` | `@Component` | `@RestController`, `@Repository`, JPA annotation |
| `adapter.out.persistence` | `@Component`, `@Repository`, `@Transactional`, JPA annotation | `@Controller`, `@RestController` |
| `adapter.out.tago` | `@Component` | `@Controller`, JPA annotation |
| `adapter.out.ollama` | `@Component` | `@Controller`, JPA annotation |

## 7. 트랜잭션 경계

트랜잭션은 외부 API 호출을 포함한 전체 유스케이스에 길게 걸지 않는다. application service는 TAGO/Ollama 호출 흐름을 조율하고, DB 읽기/쓰기 구간은 persistence adapter 메서드에서 짧은 트랜잭션으로 처리한다.

- 기준 데이터 저장
- 위치/도착 스냅샷 저장
- 수집 실행 이력 저장
- AI 브리핑 저장

TAGO/Ollama 포트 호출은 트랜잭션 밖에서 수행한다. 호출 결과를 받은 뒤 저장 포트를 호출할 때만 짧은 DB 트랜잭션을 연다.

## 8. 테스트 기준

- `domain`은 순수 단위 테스트로 검증한다.
- `application.service`는 port mock을 사용해 유스케이스 흐름을 검증한다.
- `adapter.out.persistence`는 JPA 테스트로 매핑과 쿼리를 검증한다.
- `adapter.out.tago`는 실제 TAGO 호출 전까지 fixture 기반 파싱 테스트를 우선한다.
- `adapter.in.web`은 MVC 테스트로 화면/API 요청과 응답 모델을 검증한다.

## 9. 전환 원칙

현재 Waypoint에는 구현 코드가 거의 없으므로 처음부터 클린 아키텍처 패키지로 시작한다. 전환기 예외 패키지는 만들지 않는다.

최상위 단순 계층 패키지로 다음 이름은 새 기능에서 사용하지 않는다.

- `controller`
- `service`
- `repository`
- `data`

`application.service`와 `adapter.out.persistence.repository`처럼 클린 아키텍처 경계 안에서 쓰는 세부 패키지는 허용한다. Spring Data repository와 JPA entity는 `adapter.out.persistence` 아래에 둔다.
