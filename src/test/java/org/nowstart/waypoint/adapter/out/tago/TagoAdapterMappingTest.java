package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TagoAdapterMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("노선 기본정보 응답 필드를 노선 데이터로 변환한다")
    void mapRouteInfo() throws Exception {
        // given: 노선 기본정보 공식 응답 필드
        TagoClient client = mock(TagoClient.class);
        JsonNode item = objectMapper.readTree("""
                {
                  "routeid": "CWB123",
                  "routeno": "101",
                  "routetp": "간선버스",
                  "startnodenm": "기점",
                  "endnodenm": "종점",
                  "intervaltime": "12",
                  "intervalsattime": "18",
                  "intervalsuntime": "20",
                  "startvehicletime": "0520",
                  "endvehicletime": "2310"
                }
                """);
        given(client.fetchItems("BusRouteInfoInqireService", "getRouteInfoIem", Map.of(
                "cityCode", "38010",
                "routeId", "CWB123"
        ))).willReturn(List.of(item));
        TagoRouteAdapter adapter = new TagoRouteAdapter(client);

        // when: 노선 상세 응답을 포트 모델로 변환한다
        LoadTagoRoutePort.TagoRoute route = adapter.loadRouteInfo("38010", "CWB123").orElseThrow();

        // then: 배차간격과 운행 시간 필드를 보존한다
        then(route.sourceRouteId()).isEqualTo("CWB123");
        then(route.routeNo()).isEqualTo("101");
        then(route.routeType()).isEqualTo("간선버스");
        then(route.weekdayIntervalMinutes()).isEqualTo(12);
        then(route.saturdayIntervalMinutes()).isEqualTo(18);
        then(route.sundayIntervalMinutes()).isEqualTo(20);
        then(route.firstVehicleTime()).isEqualTo("0520");
        then(route.lastVehicleTime()).isEqualTo("2310");
    }

    @Test
    @DisplayName("노선별 위치정보 응답 필드를 위치 스냅샷 데이터로 변환한다")
    void mapLocation() throws Exception {
        // given: 노선별 위치정보 공식 응답 필드
        TagoClient client = mock(TagoClient.class);
        JsonNode item = objectMapper.readTree("""
                {
                  "routenm": "101",
                  "vehicleno": "경남71자1234",
                  "nodeid": "CWS001",
                  "nodeord": "7",
                  "gpslati": "35.227",
                  "gpslong": "128.681"
                }
                """);
        given(client.fetchItems("BusLcInfoInqireService", "getRouteAcctoBusLcList", Map.of(
                "cityCode", "38010",
                "routeId", "CWB123"
        ))).willReturn(List.of(item));
        TagoLocationAdapter adapter = new TagoLocationAdapter(client);

        // when: 위치 응답을 스냅샷 포트 모델로 변환한다
        LoadTagoLocationPort.TagoBusLocation location = adapter.loadBusLocations("38010", "CWB123").getFirst();

        // then: 차량번호, 정류소 순번, 좌표를 보존한다
        then(location.sourceRouteId()).isEqualTo("CWB123");
        then(location.routeNo()).isEqualTo("101");
        then(location.vehicleNo()).isEqualTo("경남71자1234");
        then(location.sourceNodeId()).isEqualTo("CWS001");
        then(location.nodeOrder()).isEqualTo(7);
        then(location.gpsLatitude()).isEqualTo(35.227);
        then(location.gpsLongitude()).isEqualTo(128.681);
        then(location.collectedAt()).isNotNull();
    }

    @Test
    @DisplayName("정류소별 도착정보 응답 필드를 도착 스냅샷 데이터로 변환한다")
    void mapArrival() throws Exception {
        // given: 정류소별 도착정보 공식 응답 필드
        TagoClient client = mock(TagoClient.class);
        JsonNode item = objectMapper.readTree("""
                {
                  "nodeid": "CWS001",
                  "nodenm": "창원시청",
                  "routeid": "CWB123",
                  "routeno": "101",
                  "routetp": "간선버스",
                  "arrprevstationcnt": "3",
                  "arrtime": "125",
                  "vehicletp": "일반"
                }
                """);
        given(client.fetchItems("ArvlInfoInqireService", "getSttnAcctoArvlPrearngeInfoList", Map.of(
                "cityCode", "38010",
                "nodeId", "CWS001"
        ))).willReturn(List.of(item));
        TagoArrivalAdapter adapter = new TagoArrivalAdapter(client);

        // when: 도착 응답을 스냅샷 포트 모델로 변환한다
        LoadTagoArrivalPort.TagoBusArrival arrival = adapter.loadArrivals("38010", "CWS001").getFirst();

        // then: 남은 정류장 수와 남은 시간을 보존한다
        then(arrival.sourceNodeId()).isEqualTo("CWS001");
        then(arrival.nodeName()).isEqualTo("창원시청");
        then(arrival.sourceRouteId()).isEqualTo("CWB123");
        then(arrival.arrivalRemainingStationCount()).isEqualTo(3);
        then(arrival.arrivalRemainingMinutes()).isEqualTo(3);
        then(arrival.arrivalExpectedAt()).isNotNull();
        then(arrival.vehicleType()).isEqualTo("일반");
        then(arrival.collectedAt()).isNotNull();
    }
}
