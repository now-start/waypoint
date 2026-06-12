# 데이터 모델 초안

## 1. 목적

이 문서는 Waypoint MVP에서 저장할 데이터의 초안을 정의한다. 구현 단계에서는 실제 TAGO 응답 필드와 JPA 설계에 맞춰 조정한다.

MVP의 데이터 모델은 창원시 전체 노선의 운영현황, 이상징후, AI 브리핑을 만들 수 있을 만큼만 둔다.

## 2. 설계 원칙

- TAGO 원본 ID를 내부 식별자와 분리한다.
- 내부 FK는 `bus_route_id`, `bus_stop_id`처럼 테이블 의미가 드러나는 이름을 사용한다.
- TAGO 원본 ID는 `source_route_id`, `source_node_id`처럼 `source_` 접두사를 사용한다.
- 수집 이력은 계산 결과와 분리한다.
- 실시간 데이터는 항상 `collected_at`을 가진다.
- 이상징후는 계산 당시의 근거 값을 함께 저장한다.
- AI 브리핑은 입력 스냅샷과 검증된 출력 JSON을 함께 저장한다.

## 3. 테이블 후보

### 3.1 `bus_routes`

노선 기준 데이터.

필드 후보:

- `id`
- `city_code`
- `source_route_id`
- `route_no`
- `route_type`
- `start_node_name`
- `end_node_name`
- `weekday_interval_minutes`
- `saturday_interval_minutes`
- `sunday_interval_minutes`
- `first_vehicle_time`
- `last_vehicle_time`
- `created_at`
- `updated_at`

제약 후보:

- `city_code`, `source_route_id` 유니크

### 3.2 `bus_stops`

정류소 기준 데이터.

필드 후보:

- `id`
- `city_code`
- `source_node_id`
- `node_no`
- `node_name`
- `gps_latitude`
- `gps_longitude`
- `last_arrival_collected_at`
- `created_at`
- `updated_at`

제약 후보:

- `city_code`, `source_node_id` 유니크

### 3.3 `route_stops`

노선별 경유 정류소.

필드 후보:

- `id`
- `bus_route_id`
- `bus_stop_id`
- `node_order`
- `direction`
- `created_at`
- `updated_at`

제약 후보:

- `bus_route_id`, `node_order` 유니크
- `bus_route_id`, `bus_stop_id`, `direction` 유니크

### 3.4 `bus_location_snapshots`

노선별 버스 위치 수집 이력.

필드 후보:

- `id`
- `bus_route_id`
- `source_route_id`
- `route_no`
- `vehicle_no`
- `source_node_id`
- `node_order`
- `gps_latitude`
- `gps_longitude`
- `collected_at`
- `created_at`

인덱스 후보:

- `bus_route_id`, `collected_at`
- `vehicle_no`, `collected_at`

### 3.5 `bus_arrival_snapshots`

정류소별 도착정보 수집 이력.

필드 후보:

- `id`
- `bus_stop_id`
- `source_node_id`
- `node_name`
- `bus_route_id`
- `source_route_id`
- `route_no`
- `route_type`
- `arrival_remaining_station_count`
- `arrival_remaining_minutes`
- `arrival_expected_at`
- `vehicle_type`
- `collected_at`
- `created_at`

인덱스 후보:

- `bus_stop_id`, `collected_at`
- `bus_route_id`, `collected_at`

비고:

- `arrival_remaining_minutes`와 `arrival_expected_at`은 TAGO `arrtime` 초 단위 응답에서 변환한다.
- MVP 이상징후 계산은 이 필드에 의존하지 않는다.

### 3.6 `collection_runs`

TAGO API 수집 실행 단위.

필드 후보:

- `id`
- `api_type`
- `request_key`
- `request_params_json`
- `started_at`
- `finished_at`
- `status`
- `http_status`
- `result_code`
- `result_message`
- `row_count`
- `error_message`

상태 후보:

- `SUCCESS`
- `EMPTY`
- `FAILED`
- `PARTIAL`

비고:

- MVP에서는 원본 전문 저장보다 재현 가능한 메타데이터 저장을 우선한다.
- 원본 전문은 MVP에서 저장하지 않고, 필요 시 파일 또는 별도 저장소 참조값을 확장한다.

### 3.7 `operation_anomaly`

서버가 계산한 이상징후.

필드 후보:

- `id`
- `type`
- `severity`
- `bus_route_id`
- `route_no`
- `bus_stop_id`
- `source_node_id`
- `summary`
- `observed_at`
- `metric_name`
- `metric_value`
- `threshold_value`
- `evidence_json`
- `created_at`

유형 후보:

- `HEADWAY_TOO_WIDE`
- `HEADWAY_TOO_NARROW`
- `LOCATION_STALE`

심각도 후보:

- `NORMAL`
- `CAUTION`
- `WARNING`

### 3.8 `ai_briefing`

AI 브리핑 생성 이력.

필드 후보:

- `id`
- `briefing_at`
- `period_start`
- `period_end`
- `model_name`
- `prompt_version`
- `input_snapshot_json`
- `output_json`
- `status`
- `error_message`
- `created_at`

상태 후보:

- `SUCCESS`
- `FAILED`

## 4. 계산 모델

### 운영현황

운영현황은 기준 데이터와 최신 수집 데이터를 조합해서 만든다.

필요 데이터:

- 노선 기준정보
- 최근 위치정보
- 최근 도착정보 수신 시각
- 마지막 수집 성공 시각

계산 결과:

- 관측 차량 수
- 평균 차량 간격
- 최대 차량 간격
- 위치 갱신 지연 여부
- 도착정보 최근성
- 노선 상태

### 이상징후

이상징후는 주기적으로 계산하거나 화면 조회 시 계산할 수 있다.

저장 우선 방식:

- MVP에서는 계산 결과를 `operation_anomaly`에 저장한다.
- AI 브리핑은 저장된 이상징후를 입력으로 사용한다.

MVP 이상징후:

- 차량 간격 벌어짐
- 차량 간격 붙음
- 위치 갱신 지연

## 5. JSON 필드 사용 기준

`evidence_json`, `input_snapshot_json`, `output_json`은 MVP에서 빠르게 근거 구조와 AI 입출력을 보존하기 위해 사용한다.

원칙:

- 검색과 정렬이 필요한 값은 컬럼으로 분리한다.
- AI 입력 재현에 필요한 스냅샷은 JSON으로 저장한다.
- AI 출력은 검증된 JSON 구조로 저장한다.
- JSON에만 중요한 도메인 값을 숨기지 않는다.

## 6. MVP에서 제외한 데이터

도착 예정 시간 변동 기반 이상징후는 TAGO `arrtime` 기반 수집 데이터가 충분히 쌓인 뒤 확장한다.

후보 필드:

- `arrival_remaining_minutes`
- `arrival_expected_at`

## 7. DB 기준

MVP 로컬 개발은 H2 `MODE=MariaDB`로 시작한다. 운영 DB는 MariaDB로 한다.

JSON 필드는 MariaDB `json` 타입 또는 호환 문자열 타입 후보로 둔다. H2 로컬에서는 문자열 또는 호환 타입으로 저장할 수 있다.

## 8. 미정 사항

- 방향 정보 산출 방식
- 차량번호(`vehicleno`)의 장기 안정성
- MariaDB JSON 필드 매핑 방식
