# TAGO API 연동 명세

## 1. 목적

이 문서는 Waypoint MVP에서 사용할 TAGO 공공 API의 연동 범위를 정리한다. 2026-06-12 기준 공공데이터포털 상세기능 문서에서 노선 기본정보, 위치정보, 도착정보 주요 필드를 확인했다.

## 2. 공통 원칙

- 응답 포맷은 JSON을 우선 사용한다.
- 모든 요청은 공공데이터포털 인증키를 사용한다.
- MVP 대상 지역은 창원시로 제한한다.
- MVP 수집 대상은 창원시 전체 노선으로 한다.
- `cityCode`는 TAGO 도시코드 목록 조회로 확정한다.
- 인증키는 `TAGO_SERVICE_KEY` 환경변수로 관리한다.
- 외부 API 호출 결과는 계산 결과와 분리해서 추적 가능하게 저장한다.
- MVP에서는 원본 전문 영구 저장보다 요청 파라미터, HTTP 상태, TAGO 결과코드, 행 수, 오류 메시지 같은 재현용 메타데이터 저장을 우선한다.

공통 파라미터 후보:

- `serviceKey`: 공공데이터포털 인증키
- `_type`: `json`
- `pageNo`: 페이지 번호
- `numOfRows`: 한 페이지 결과 수
- `cityCode`: 도시코드

## 3. 사용할 API

### 3.1 버스 노선정보

용도:

- 창원시 전체 노선 목록 확보
- 노선 기본정보 확보
- 노선별 경유 정류소 목록 확보

공공데이터포털 기준:

- 데이터명: 국토교통부_(TAGO)_버스노선정보
- 서비스: `BusRouteInfoInqireService`
- 주요 기능: 노선정보항목 조회, 노선번호목록 조회, 노선별경유정류소목록 조회, 도시코드 목록 조회
- 노선 기본정보 조회 주소: `http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteInfoIem`
- 구현 operation:
  - 도시코드 목록 조회: `getCtyCodeList`
  - 노선번호목록 조회: `getRouteNoList`
  - 노선정보항목 조회: `getRouteInfoIem`
  - 노선별경유정류소목록 조회: `getRouteAcctoThrghSttnList`

MVP 저장 후보:

- `sourceRouteId`
- `routeNo`
- `routeType`
- `startNodeName`
- `endNodeName`
- `startVehicleTime`
- `endVehicleTime`
- `weekdayIntervalMinutes`
- `saturdayIntervalMinutes`
- `sundayIntervalMinutes`

### 3.2 버스 도착정보

용도:

- 정류소 기준 도착 예정 정보 확인
- 도착정보 수신 상태와 최근성 확인
- 이상징후 근거 데이터 생성
- 도착 예정 시간 변동 탐지는 응답 필드 확인 후 확장 범위로 둔다.

공공데이터포털 기준:

- 데이터명: 국토교통부_(TAGO)_버스도착정보
- 서비스: `ArvlInfoInqireService`
- 주요 기능: 정류소별 도착예정정보 목록 조회, 정류소별 특정노선버스 도착예정정보 목록 조회, 도시코드 목록 조회
- 정류소별 도착예정정보 조회 주소: `http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList`
- 구현 operation:
  - 정류소별 도착예정정보 목록 조회: `getSttnAcctoArvlPrearngeInfoList`

MVP 저장 후보:

- `sourceNodeId`
- `nodeName`
- `sourceRouteId`
- `routeNo`
- `routeType`
- `arrivalRemainingStationCount`
- `arrivalRemainingMinutes` 또는 `arrivalExpectedAt`
- `vehicleType`
- `collectedAt`

### 3.3 버스 위치정보

용도:

- 노선별 운행 차량 위치 확인
- 차량 간격 계산
- 위치 갱신 지연 탐지

공공데이터포털 기준:

- 데이터명: 국토교통부_(TAGO)_버스위치정보
- 서비스: `BusLcInfoInqireService`
- 주요 기능: 노선별버스위치 목록조회, 노선별특정정류소접근 버스위치정보조회, 도시코드 목록 조회
- 노선별 버스 위치 조회 주소: `http://apis.data.go.kr/1613000/BusLcInfoInqireService/getRouteAcctoBusLcList`
- 구현 operation:
  - 노선별 버스 위치 목록 조회: `getRouteAcctoBusLcList`

MVP 저장 후보:

- `sourceRouteId`
- `routeNo`
- `vehicleNo`
- `sourceNodeId`
- `nodeOrder`
- `gpsLatitude`
- `gpsLongitude`
- `collectedAt`

### 3.4 버스 정류소정보

용도:

- 정류소 기준 데이터 보강
- 노선 경유 정류소와 도착정보 매핑
- 향후 지도 기반 표시 준비

공공데이터포털 기준:

- 데이터명: 국토교통부_(TAGO)_버스정류소정보
- 서비스: `BusSttnInfoInqireService`
- 주요 기능: 좌표기반근접정류소 목록조회, 정류소번호 목록조회, 정류소별경유노선 목록조회, 도시코드 목록 조회
- 좌표 기반 근접 정류소 조회 주소: `http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList`

MVP 저장 후보:

- `sourceNodeId`
- `nodeNo`
- `nodeName`
- `gpsLatitude`
- `gpsLongitude`
- `cityCode`

## 4. 수집 전략

### 기준 데이터

- 도시코드는 수동 또는 초기화 배치로 확정한다.
- 창원시 전체 노선 목록과 노선별 경유 정류소는 앱 시작 후 수동 수집 또는 관리용 배치로 갱신한다.
- 기준 데이터는 실시간 데이터보다 낮은 빈도로 갱신한다.

### 실시간 데이터

- 버스 위치정보는 창원시 전체 노선을 대상으로 주기 수집한다.
- 도착정보는 전체 노선 운영현황 보조용 관찰 대상 정류소를 정해 수집한다.
- 모든 수집 결과는 `collectedAt`을 가진다.

초기 수집 주기 후보:

- 위치정보: 30초에서 60초
- 도착정보: 60초
- 기준 데이터: 수동 또는 1일 1회

## 5. 오류 처리

구분:

- HTTP 실패
- TAGO 결과코드 실패
- 빈 응답
- 필수 필드 누락
- 파싱 실패
- 저장 실패

처리 원칙:

- 수집 실패는 계산 대상에서 제외한다.
- 빈 응답은 정상 운행 데이터로 해석하지 않는다.
- 마지막 성공 수집 시각을 유지한다.
- 실패 응답은 운영현황 화면의 최근 수집 시각과 상태 판단에 반영한다.

## 6. 확인 및 최초 실측 항목

확인된 항목:

- 노선 기본정보는 `routeid`, `routeno`, `routetp`, `startnodenm`, `endnodenm`, `startvehicletime`, `endvehicletime`, `intervaltime`, `intervalsattime`, `intervalsuntime`을 사용한다.
- 위치정보는 `routenm`, `vehicleno`, `nodeid`, `nodeord`, `gpslati`, `gpslong`을 사용한다.
- 도착정보는 `nodeid`, `nodenm`, `routeid`, `routeno`, `routetp`, `arrprevstationcnt`, `arrtime`, `vehicletp`을 사용한다.
- `arrtime`은 남은 초로 수신하고, 서버에서 남은 분과 예상 시각으로 변환한다.

최초 실측 시 확인할 항목:

- 창원시 `cityCode`가 환경변수 없이 도시코드 목록에서 정상 해석되는지 확인한다.
- 창원시 노선/경유정류소 응답의 필수 ID 누락률을 확인한다.
- 위치정보의 `vehicleno`가 수집 주기 사이에서 안정적으로 유지되는지 확인한다.
- 개발계정 트래픽 한도 안에서 전체 노선 위치 수집 주기를 조정한다.

## 7. 참고 링크

- 국토교통부_(TAGO)_버스노선정보: https://www.data.go.kr/data/15098529/openapi.do
- 국토교통부_(TAGO)_버스도착정보: https://www.data.go.kr/data/15098530/openapi.do
- 국토교통부_(TAGO)_버스위치정보: https://www.data.go.kr/data/15098533/openapi.do
- 국토교통부_(TAGO)_버스정류소정보: https://www.data.go.kr/data/15098534/openapi.do
