package org.nowstart.waypoint.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CollectionInteractorTest {

    @Mock
    private TagoCityCodeResolver cityCodeResolver;

    @Mock
    private LoadTagoArrivalPort arrivalPort;

    @Mock
    private LoadTransitDataPort loadTransitDataPort;

    @Mock
    private LoadTagoLocationPort locationPort;

    @Mock
    private SaveTransitDataPort saveTransitDataPort;

    @Test
    @DisplayName("수집된 정류장 기준 데이터가 없으면 빈 수집 결과를 반환하고 TAGO를 호출하지 않는다")
    void collectAllStopsWhenStopsEmpty() {
        // given: 수집된 정류장 기준 데이터가 없는 도착 수집기
        given(cityCodeResolver.resolve()).willReturn("38010");
        given(loadTransitDataPort.loadStops("38010")).willReturn(List.of());

        BusArrivalCollectionInteractor interactor = new BusArrivalCollectionInteractor(
                cityCodeResolver,
                loadTransitDataPort,
                arrivalPort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 1L)
        );

        // when: 전체 정류장 도착정보 수집을 실행한다
        CollectionResult result = interactor.collectAllStops();

        // then: 빈 수집 결과를 반환하고 TAGO 도착 포트를 호출하지 않는다
        then(result.status()).isEqualTo(CollectionStatus.EMPTY);
        then(result.rowCount()).isZero();
        then(result.failureCount()).isZero();
        verifyNoInteractions(arrivalPort);
        org.mockito.BDDMockito.then(saveTransitDataPort).should().finishCollectionRun(
                1L,
                CollectionStatus.EMPTY,
                200,
                "OK",
                "수집된 정류장 기준 데이터가 없습니다.",
                0,
                null
        );
    }

    @Test
    @DisplayName("도착정보 수집은 전체 정류장을 순회하고 일부 정류장 실패 시 부분 성공을 반환한다")
    void collectArrivalsForAllStopsWhenOneStopFails() {
        // given: 두 정류장 중 한 정류장의 TAGO 도착 수집이 실패하는 도착 수집기
        LoadTransitDataPort.StopReference successStop = stop("CWS001", "창원역");
        LoadTransitDataPort.StopReference failedStop = stop("CWS002", "시청");
        List<LoadTagoArrivalPort.TagoBusArrival> arrivals = List.of(arrival("CWS001", "CWB101"));

        given(cityCodeResolver.resolve()).willReturn("38010");
        given(loadTransitDataPort.loadStops("38010")).willReturn(List.of(successStop, failedStop));
        given(arrivalPort.loadArrivals("38010", "CWS001")).willReturn(arrivals);
        given(saveTransitDataPort.saveArrivalSnapshots("38010", "CWS001", arrivals)).willReturn(1);
        given(arrivalPort.loadArrivals("38010", "CWS002"))
                .willThrow(new IllegalStateException("TAGO 도착 수집 실패"));

        BusArrivalCollectionInteractor interactor = new BusArrivalCollectionInteractor(
                cityCodeResolver,
                loadTransitDataPort,
                arrivalPort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 2L)
        );

        // when: 전체 정류장 도착정보 수집을 실행한다
        CollectionResult result = interactor.collectAllStops();

        // then: 성공 저장 건수와 실패 정류장 수를 부분 성공 결과로 반환한다
        then(result.status()).isEqualTo(CollectionStatus.PARTIAL);
        then(result.rowCount()).isEqualTo(1);
        then(result.failureCount()).isEqualTo(1);
        org.mockito.BDDMockito.then(saveTransitDataPort).should().finishCollectionRun(
                2L,
                CollectionStatus.PARTIAL,
                200,
                "OK",
                "arrivals=1, stopFailures=1, failedStopIds=[CWS002]",
                1,
                null
        );
    }

    @Test
    @DisplayName("버스 위치 수집은 일부 노선 실패 시 성공 저장 건수와 실패 건수를 부분 성공으로 반환한다")
    void collectLocationsWhenOneRouteFails() {
        // given: 두 노선 중 한 노선의 TAGO 위치 수집이 실패하는 위치 수집기
        LoadTransitDataPort.RouteReference successRoute = route("CWB101", "101");
        LoadTransitDataPort.RouteReference failedRoute = route("CWB102", "102");
        List<LoadTagoLocationPort.TagoBusLocation> locations = List.of(
                location("CWB101", "경남71자1001"),
                location("CWB101", "경남71자1002")
        );

        given(cityCodeResolver.resolve()).willReturn("38010");
        given(loadTransitDataPort.loadRoutes("38010")).willReturn(List.of(successRoute, failedRoute));
        given(locationPort.loadBusLocations("38010", "CWB101")).willReturn(locations);
        given(saveTransitDataPort.saveLocationSnapshots("38010", "CWB101", locations)).willReturn(2);
        given(locationPort.loadBusLocations("38010", "CWB102"))
                .willThrow(new IllegalStateException("TAGO 위치 수집 실패"));

        BusLocationCollectionInteractor interactor = new BusLocationCollectionInteractor(
                cityCodeResolver,
                loadTransitDataPort,
                locationPort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 2L)
        );

        // when: 전체 노선 위치 수집을 실행한다
        CollectionResult result = interactor.collect();

        // then: 성공 저장 건수와 실패 노선 수를 부분 성공 결과로 반환한다
        then(result.status()).isEqualTo(CollectionStatus.PARTIAL);
        then(result.rowCount()).isEqualTo(2);
        then(result.failureCount()).isEqualTo(1);
        org.mockito.BDDMockito.then(saveTransitDataPort).should().finishCollectionRun(
                2L,
                CollectionStatus.PARTIAL,
                200,
                "OK",
                "locations=2, routeFailures=1",
                2,
                null
        );
    }

    private static CollectionRunSupport runSupport(SaveTransitDataPort saveTransitDataPort, Long runId) {
        given(saveTransitDataPort.startCollectionRun(any(CollectionApiType.class), anyString(), anyString()))
                .willReturn(runId);
        return new CollectionRunSupport(saveTransitDataPort);
    }

    private static LoadTransitDataPort.RouteReference route(String sourceRouteId, String routeNo) {
        return new LoadTransitDataPort.RouteReference(1L, "38010", sourceRouteId, routeNo);
    }

    private static LoadTransitDataPort.StopReference stop(String sourceNodeId, String nodeName) {
        return new LoadTransitDataPort.StopReference(1L, "38010", sourceNodeId, nodeName, null);
    }

    private static LoadTagoLocationPort.TagoBusLocation location(String sourceRouteId, String vehicleNo) {
        return new LoadTagoLocationPort.TagoBusLocation(
                sourceRouteId,
                "101",
                vehicleNo,
                "CWS001",
                1,
                35.227,
                128.681,
                Instant.parse("2026-06-13T00:00:00Z")
        );
    }

    private static LoadTagoArrivalPort.TagoBusArrival arrival(String sourceNodeId, String sourceRouteId) {
        return new LoadTagoArrivalPort.TagoBusArrival(
                sourceNodeId,
                "창원역",
                sourceRouteId,
                "101",
                "간선",
                3,
                5,
                Instant.parse("2026-06-13T00:05:00Z"),
                "일반",
                Instant.parse("2026-06-13T00:00:00Z")
        );
    }
}
