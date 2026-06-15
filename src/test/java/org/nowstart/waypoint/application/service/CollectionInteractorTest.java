package org.nowstart.waypoint.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.config.TagoCollectionProperties;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private LoadTagoRoutePort routePort;

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
                runSupport(saveTransitDataPort, 1L),
                collectionProperties(1)
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
        given(saveTransitDataPort.saveArrivalSnapshots("38010", arrivals)).willReturn(1);
        given(arrivalPort.loadArrivals("38010", "CWS002"))
                .willThrow(new IllegalStateException("TAGO 도착 수집 실패"));

        BusArrivalCollectionInteractor interactor = new BusArrivalCollectionInteractor(
                cityCodeResolver,
                loadTransitDataPort,
                arrivalPort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 2L),
                collectionProperties(1)
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
        given(saveTransitDataPort.saveLocationSnapshots("38010", locations)).willReturn(2);
        given(locationPort.loadBusLocations("38010", "CWB102"))
                .willThrow(new IllegalStateException("TAGO 위치 수집 실패"));

        BusLocationCollectionInteractor interactor = new BusLocationCollectionInteractor(
                cityCodeResolver,
                loadTransitDataPort,
                locationPort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 2L),
                collectionProperties(1)
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
                "locations=2, routeFailures=1, skippedInactiveRoutes=0, failedRouteIds=[CWB102]",
                2,
                null
        );
    }

    @Test
    @DisplayName("노선 운행 시간 판정은 첫차와 막차 사이에만 활성으로 본다")
    void routeOperationWindowActiveOnlyBetweenFirstAndLastVehicleTime() {
        // given: 05:20부터 23:10까지 운행하는 노선
        LoadTransitDataPort.RouteReference route = new LoadTransitDataPort.RouteReference(
                "38010",
                "CWB101",
                "101",
                "0520",
                "2310"
        );

        // then: 운행 시간 안에서는 활성이고, 운행 시간 밖에서는 비활성이다
        then(RouteOperationWindow.isActive(route, LocalTime.of(5, 20))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(12, 0))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 10))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(4, 59))).isFalse();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 11))).isFalse();
    }

    @Test
    @DisplayName("막차가 자정을 넘는 노선은 날짜 경계를 넘어 운행 중으로 본다")
    void routeOperationWindowSupportsOvernightRoutes() {
        // given: 23:30부터 다음날 01:10까지 운행하는 노선
        LoadTransitDataPort.RouteReference route = new LoadTransitDataPort.RouteReference(
                "38010",
                "CWB900",
                "900",
                "2330",
                "0110"
        );

        // then: 자정 전후 모두 운행 시간으로 본다
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 40))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(0, 30))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(2, 0))).isFalse();
    }

    @Test
    @DisplayName("첫차와 막차 시간이 없으면 기본 운행 시간 05:00부터 23:30까지로 본다")
    void routeOperationWindowUsesDefaultWindowWhenRouteTimesAreMissing() {
        // given: 첫차와 막차 시간이 아직 저장되지 않은 노선
        LoadTransitDataPort.RouteReference route = new LoadTransitDataPort.RouteReference(
                "38010",
                "CWB101",
                "101",
                null,
                null
        );

        // then: 기본 운행 시간 안에서만 활성으로 본다
        then(RouteOperationWindow.isActive(route, LocalTime.of(5, 0))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 30))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(4, 59))).isFalse();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 31))).isFalse();
    }

    @Test
    @DisplayName("첫차와 막차 중 하나만 없으면 기본 운행 시간 전체를 사용한다")
    void routeOperationWindowUsesDefaultWindowWhenEitherRouteTimeIsMissing() {
        // given: 첫차만 있고 막차가 없는 노선
        LoadTransitDataPort.RouteReference route = new LoadTransitDataPort.RouteReference(
                "38010",
                "CWB101",
                "101",
                "2330",
                null
        );

        // then: 24시간 운행으로 열지 않고 기본 운행 시간으로 제한한다
        then(RouteOperationWindow.isActive(route, LocalTime.of(12, 0))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 31))).isFalse();
    }

    @Test
    @DisplayName("기준정보 수집은 노선 저장 직후 위치 수집 대기를 해제한다")
    void collectReferenceDataMarksRoutesReadyAfterSavingRoutes() {
        // given: 노선 기준정보 저장까지 성공하는 기준정보 수집기
        LoadTagoRoutePort.TagoRoute route = tagoRoute("CWB101", "101");
        ReferenceDataCollectionState collectionState = new ReferenceDataCollectionState();
        given(cityCodeResolver.resolve()).willReturn("38010");
        given(routePort.loadRoutes("38010")).willReturn(List.of(route));
        given(routePort.loadRouteInfo("38010", "CWB101")).willAnswer(invocation -> {
            assertThat(collectionState.shouldDeferLocationCollection()).isFalse();
            return Optional.of(route);
        });
        given(saveTransitDataPort.saveRoutes("38010", List.of(route))).willReturn(1);
        given(routePort.loadRouteStops("38010", "CWB101")).willReturn(List.of());

        ReferenceDataCollectionInteractor interactor = new ReferenceDataCollectionInteractor(
                cityCodeResolver,
                routePort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 3L),
                collectionState,
                collectionProperties(1)
        );

        // when: 기준정보 수집을 실행한다
        CollectionResult result = interactor.collect();

        // then: 정류장 수집이 끝나기 전이라도 노선 저장 뒤에는 위치 수집 대기를 해제한다
        then(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        then(collectionState.shouldDeferLocationCollection()).isFalse();
    }

    @Test
    @DisplayName("기준정보 수집은 중복 노선을 TAGO 상세 호출 전에 제거한다")
    void collectReferenceDataDeduplicatesRoutesBeforeTagoDetailCalls() {
        // given: TAGO 노선 목록에 같은 원본 노선 ID가 중복으로 들어온다
        LoadTagoRoutePort.TagoRoute originalRoute = tagoRoute("CWB101", "101");
        LoadTagoRoutePort.TagoRoute latestRoute = tagoRoute("CWB101", "101-1");
        given(cityCodeResolver.resolve()).willReturn("38010");
        given(routePort.loadRoutes("38010")).willReturn(List.of(originalRoute, latestRoute));
        given(saveTransitDataPort.saveRoutes(eq("38010"), any())).willReturn(1);
        given(routePort.loadRouteInfo("38010", "CWB101")).willReturn(Optional.of(latestRoute));
        given(routePort.loadRouteStops("38010", "CWB101")).willReturn(List.of());

        ReferenceDataCollectionInteractor interactor = new ReferenceDataCollectionInteractor(
                cityCodeResolver,
                routePort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 4L),
                new ReferenceDataCollectionState(),
                collectionProperties(1)
        );

        // when: 기준정보 수집을 실행한다
        CollectionResult result = interactor.collect();

        // then: 중복 원본 노선 ID에 대해 상세 API는 한 번만 호출한다
        then(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        org.mockito.BDDMockito.then(routePort).should().loadRouteInfo("38010", "CWB101");
        org.mockito.BDDMockito.then(routePort).should().loadRouteStops("38010", "CWB101");
    }

    private static CollectionRunSupport runSupport(SaveTransitDataPort saveTransitDataPort, Long runId) {
        given(saveTransitDataPort.startCollectionRun(any(CollectionApiType.class), anyString(), anyString()))
                .willReturn(runId);
        return new CollectionRunSupport(saveTransitDataPort);
    }

    private static LoadTransitDataPort.RouteReference route(String sourceRouteId, String routeNo) {
        return new LoadTransitDataPort.RouteReference("38010", sourceRouteId, routeNo, "0000", "0000");
    }

    private static LoadTransitDataPort.StopReference stop(String sourceNodeId, String nodeName) {
        return new LoadTransitDataPort.StopReference("38010", sourceNodeId, nodeName, null);
    }

    private static LoadTagoRoutePort.TagoRoute tagoRoute(String sourceRouteId, String routeNo) {
        return new LoadTagoRoutePort.TagoRoute(
                sourceRouteId,
                routeNo,
                "간선",
                "기점",
                "종점",
                10,
                15,
                20,
                "0500",
                "2300"
        );
    }

    private static TagoCollectionProperties collectionProperties(int referenceDataConcurrency) {
        return new TagoCollectionProperties(
                referenceDataConcurrency,
                referenceDataConcurrency,
                referenceDataConcurrency,
                25
        );
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
