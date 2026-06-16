package org.nowstart.waypoint.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.application.port.in.QueryTransitMapUseCase;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TransitMapInteractorTest {

    @Test
    @DisplayName("지도 조회는 노선 경로와 차량별 최신 위치를 관제 지도 모델로 반환한다")
    void getMapReturnsRoutePathsAndLatestVehicles() {
        // given: 경로 좌표와 같은 차량의 중복 위치 스냅샷이 있는 지도 조회기
        LoadTransitDataPort loadTransitDataPort = mock(LoadTransitDataPort.class);
        TagoCityCodeResolver cityCodeResolver = mock(TagoCityCodeResolver.class);
        Instant recent = Instant.now().minusSeconds(20);
        Instant old = Instant.now().minusSeconds(600);
        given(cityCodeResolver.resolve()).willReturn("38010");
        given(loadTransitDataPort.loadRoutePathStops("38010", 100_000)).willReturn(List.of(
                routeStop("CWB101", "101", 1, "CWS001", 35.22, 128.68),
                routeStop("CWB101", "101", 2, "CWS002", 35.23, 128.69),
                routeStop("CWB102", "102", 1, "CWS003", 35.24, 128.70)
        ));
        given(loadTransitDataPort.loadRecentLocationSnapshots(eq("38010"), argThat(since -> since.isBefore(old)), eq(10_000)))
                .willReturn(List.of(
                location("CWB101", "101", "경남71자1001", 2, 35.23, 128.69, recent),
                location("CWB101", "101", "경남71자1001", 1, 35.22, 128.68, old),
                location("CWB102", "102", "경남71자1002", 1, 35.24, 128.70, old)
        ));
        TransitMapInteractor interactor = new TransitMapInteractor(loadTransitDataPort, cityCodeResolver);

        // when: 관제 지도 모델을 조회한다
        QueryTransitMapUseCase.TransitMapView map = interactor.getMap();

        // then: 차량은 차량번호별 최신 위치만 남고, 오래된 위치는 지연 집계에 포함된다
        then(map.routes()).hasSize(2);
        then(map.routes().getFirst().routeNo()).isEqualTo("101");
        then(map.routes().getFirst().stops()).hasSize(2);
        then(map.vehicles()).hasSize(2);
        then(map.vehicles().getFirst().vehicleNo()).isEqualTo("경남71자1001");
        then(map.vehicles().getFirst().nodeOrder()).isEqualTo(2);
        then(map.vehicles().getFirst().freshness()).isEqualTo("normal");
        then(map.vehicles().get(1).freshness()).isEqualTo("stale");
        then(map.summary().routeCount()).isEqualTo(2);
        then(map.summary().stopCount()).isEqualTo(3);
        then(map.summary().vehicleCount()).isEqualTo(2);
        then(map.summary().delayedVehicleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("지도 조회는 장주기 노선의 오래된 최신 위치도 stale 차량으로 유지한다")
    void getMapKeepsOldLatestVehicleAsStale() {
        // given: 30분 전 위치가 해당 차량의 최신 위치인 장주기 노선
        LoadTransitDataPort loadTransitDataPort = mock(LoadTransitDataPort.class);
        TagoCityCodeResolver cityCodeResolver = mock(TagoCityCodeResolver.class);
        Instant staleCollectedAt = Instant.now().minusSeconds(1_800);
        given(cityCodeResolver.resolve()).willReturn("38010");
        given(loadTransitDataPort.loadRoutePathStops("38010", 100_000)).willReturn(List.of(
                routeStop("CWB900", "900", 1, "CWS901", 35.22, 128.68),
                routeStop("CWB900", "900", 2, "CWS902", 35.23, 128.69)
        ));
        given(loadTransitDataPort.loadRecentLocationSnapshots(
                eq("38010"),
                argThat(since -> since.isBefore(staleCollectedAt)),
                eq(10_000)
        )).willReturn(List.of(
                location("CWB900", "900", "경남71자9001", 1, 35.22, 128.68, staleCollectedAt)
        ));
        TransitMapInteractor interactor = new TransitMapInteractor(loadTransitDataPort, cityCodeResolver);

        // when: 관제 지도 모델을 조회한다
        QueryTransitMapUseCase.TransitMapView map = interactor.getMap();

        // then: 조회창 밖으로 사라지지 않고 오래된 차량으로 표시된다
        then(map.vehicles()).hasSize(1);
        then(map.vehicles().getFirst().vehicleNo()).isEqualTo("경남71자9001");
        then(map.vehicles().getFirst().freshness()).isEqualTo("stale");
        then(map.summary().delayedVehicleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("지도 조회는 표시용 노선과 차량을 임의 개수로 자르지 않는다")
    void getMapDoesNotTrimRoutesAndVehiclesForDisplay() {
        // given: 기존 지도 표시 한도를 넘는 노선과 차량 위치가 있는 조회기
        LoadTransitDataPort loadTransitDataPort = mock(LoadTransitDataPort.class);
        TagoCityCodeResolver cityCodeResolver = mock(TagoCityCodeResolver.class);
        Instant recent = Instant.now().minusSeconds(20);
        List<LoadTransitDataPort.RoutePathStopReference> routeStops = new ArrayList<>();
        for (int routeIndex = 1; routeIndex <= 25; routeIndex += 1) {
            String routeId = "CWB%03d".formatted(routeIndex);
            routeStops.add(routeStop(routeId, "%d".formatted(routeIndex), 1, "CWS%03dA".formatted(routeIndex), 35.0 + (routeIndex * 0.001), 128.0));
            routeStops.add(routeStop(routeId, "%d".formatted(routeIndex), 2, "CWS%03dB".formatted(routeIndex), 35.0 + (routeIndex * 0.001), 128.1));
        }
        List<LoadTransitDataPort.LocationSnapshotReference> locations = new ArrayList<>();
        for (int vehicleIndex = 1; vehicleIndex <= 181; vehicleIndex += 1) {
            int routeIndex = ((vehicleIndex - 1) % 25) + 1;
            locations.add(location(
                    "CWB%03d".formatted(routeIndex),
                    "%d".formatted(routeIndex),
                    "경남71자%04d".formatted(vehicleIndex),
                    1,
                    35.0 + (routeIndex * 0.001),
                    128.0 + (vehicleIndex * 0.0001),
                    recent
            ));
        }
        given(cityCodeResolver.resolve()).willReturn("38010");
        given(loadTransitDataPort.loadRoutePathStops("38010", 100_000)).willReturn(routeStops);
        given(loadTransitDataPort.loadRecentLocationSnapshots(eq("38010"), argThat(since -> since.isBefore(recent)), eq(10_000)))
                .willReturn(locations);
        TransitMapInteractor interactor = new TransitMapInteractor(loadTransitDataPort, cityCodeResolver);

        // when: 관제 지도 모델을 조회한다
        QueryTransitMapUseCase.TransitMapView map = interactor.getMap();

        // then: 24개 노선, 180대 차량 같은 표시용 한도로 잘리지 않는다
        then(map.routes()).hasSize(25);
        then(map.vehicles()).hasSize(181);
        then(map.summary().routeCount()).isEqualTo(25);
        then(map.summary().vehicleCount()).isEqualTo(181);
    }

    private static LoadTransitDataPort.RoutePathStopReference routeStop(
            String sourceRouteId,
            String routeNo,
            int nodeOrder,
            String sourceNodeId,
            double latitude,
            double longitude
    ) {
        return new LoadTransitDataPort.RoutePathStopReference(
                sourceRouteId,
                routeNo,
                "간선",
                "기점",
                "종점",
                sourceNodeId,
                "정류소",
                nodeOrder,
                latitude,
                longitude,
                null
        );
    }

    private static LoadTransitDataPort.LocationSnapshotReference location(
            String sourceRouteId,
            String routeNo,
            String vehicleNo,
            int nodeOrder,
            double latitude,
            double longitude,
            Instant collectedAt
    ) {
        return new LoadTransitDataPort.LocationSnapshotReference(
                sourceRouteId,
                routeNo,
                vehicleNo,
                "CWS%03d".formatted(nodeOrder),
                nodeOrder,
                latitude,
                longitude,
                collectedAt
        );
    }
}
