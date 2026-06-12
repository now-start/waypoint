package org.nowstart.waypoint.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.ArrivalObservationSettings;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CollectionInteractorTest {

    @Mock
    private TagoCityCodeResolver cityCodeResolver;

    @Mock
    private ArrivalObservationSettings arrivalObservationSettings;

    @Mock
    private LoadTagoArrivalPort arrivalPort;

    @Mock
    private LoadTransitDataPort loadTransitDataPort;

    @Mock
    private LoadTagoLocationPort locationPort;

    @Mock
    private SaveTransitDataPort saveTransitDataPort;

    @Test
    @DisplayName("도착 관찰 정류소 설정이 비어 있으면 빈 수집 결과를 반환하고 TAGO를 호출하지 않는다")
    void collectObservationStopsWhenObservationStopsEmpty() {
        // given: 관찰 정류소 설정이 비어 있는 도착 수집기
        given(arrivalObservationSettings.arrivalObservationSourceNodeIds()).willReturn(List.of());

        BusArrivalCollectionInteractor interactor = new BusArrivalCollectionInteractor(
                cityCodeResolver,
                arrivalObservationSettings,
                arrivalPort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 1L)
        );

        // when: 관찰 정류소 도착정보 수집을 실행한다
        CollectionResult result = interactor.collectObservationStops();

        // then: 빈 수집 결과를 반환하고 도시코드 조회 및 TAGO 도착 포트를 호출하지 않는다
        then(result.status()).isEqualTo(CollectionStatus.EMPTY);
        then(result.rowCount()).isZero();
        then(result.failureCount()).isZero();
        org.mockito.BDDMockito.then(cityCodeResolver).should(never()).resolve();
        verifyNoInteractions(arrivalPort);
        org.mockito.BDDMockito.then(saveTransitDataPort).should().finishCollectionRun(
                1L,
                CollectionStatus.EMPTY,
                200,
                "OK",
                "waypoint.collection.arrival-observation-source-node-ids 설정이 비어 있습니다.",
                0,
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
}
