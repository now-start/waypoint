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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
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
                collectionProperties(1),
                new LocationCollectionAttemptRegistry()
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
                "locations=2, routeFailures=1, skippedInactiveRoutes=0, skippedNotDueRoutes=0, failedRouteIds=[CWB102]",
                2,
                null
        );
    }

    @Test
    @DisplayName("버스 위치 수집은 직전 시도 노선을 메모리에 표시해 즉시 재호출하지 않는다")
    void collectLocationsSkipsImmediatelyAttemptedRoutes() {
        // given: 위치 수집 직후 다시 수집이 실행되는 상황
        LoadTransitDataPort.RouteReference firstRoute = route("CWB101", "101");
        LoadTransitDataPort.RouteReference secondRoute = route("CWB102", "102");
        List<LoadTransitDataPort.RouteReference> routes = List.of(firstRoute, secondRoute);

        given(cityCodeResolver.resolve()).willReturn("38010");
        given(loadTransitDataPort.loadRoutes("38010")).willReturn(routes);
        given(locationPort.loadBusLocations("38010", "CWB101")).willReturn(List.of());
        given(locationPort.loadBusLocations("38010", "CWB102")).willReturn(List.of());
        given(saveTransitDataPort.saveLocationSnapshots(eq("38010"), any())).willReturn(0);

        BusLocationCollectionInteractor interactor = new BusLocationCollectionInteractor(
                cityCodeResolver,
                loadTransitDataPort,
                locationPort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 5L, 6L),
                collectionProperties(1),
                new LocationCollectionAttemptRegistry()
        );

        // when: 같은 프로세스에서 즉시 두 번 실행한다
        CollectionResult firstResult = interactor.collect();
        CollectionResult secondResult = interactor.collect();

        // then: 두 번째 실행은 방금 시도한 노선을 due로 보지 않는다
        then(firstResult.status()).isEqualTo(CollectionStatus.EMPTY);
        then(secondResult.status()).isEqualTo(CollectionStatus.EMPTY);
        then(secondResult.message()).contains("skippedNotDueRoutes=2");
        org.mockito.BDDMockito.then(locationPort).should(times(1)).loadBusLocations("38010", "CWB101");
        org.mockito.BDDMockito.then(locationPort).should(times(1)).loadBusLocations("38010", "CWB102");
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

    @Test
    @DisplayName("기준정보 수집은 시작/수동/스케줄 진입점이 겹쳐도 중복 실행하지 않는다")
    void collectReferenceDataSkipsOverlappingRuns() {
        // given: 기준정보 수집 중 다시 기준정보 수집이 호출된다
        LoadTagoRoutePort.TagoRoute route = tagoRoute("CWB101", "101");
        CollectionResult[] nestedResult = new CollectionResult[1];
        ReferenceDataCollectionInteractor[] interactorRef = new ReferenceDataCollectionInteractor[1];
        ReferenceDataCollectionInteractor interactor = new ReferenceDataCollectionInteractor(
                cityCodeResolver,
                routePort,
                saveTransitDataPort,
                runSupport(saveTransitDataPort, 7L),
                new ReferenceDataCollectionState(),
                collectionProperties(1)
        );
        interactorRef[0] = interactor;
        given(cityCodeResolver.resolve()).willReturn("38010");
        given(routePort.loadRoutes("38010")).willAnswer(invocation -> {
            nestedResult[0] = interactorRef[0].collect();
            return List.of(route);
        });
        given(saveTransitDataPort.saveRoutes(eq("38010"), any())).willReturn(1);
        given(routePort.loadRouteInfo("38010", "CWB101")).willReturn(Optional.of(route));
        given(routePort.loadRouteStops("38010", "CWB101")).willReturn(List.of());

        // when: 기준정보 수집을 실행한다
        CollectionResult result = interactor.collect();

        // then: 중첩 호출은 실제 TAGO 기준정보 수집을 다시 시작하지 않는다
        then(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        then(nestedResult[0].status()).isEqualTo(CollectionStatus.EMPTY);
        then(nestedResult[0].message()).isEqualTo("이미 TAGO 기준정보 수집이 진행 중입니다.");
        org.mockito.BDDMockito.then(routePort).should(times(1)).loadRoutes("38010");
    }

    private static CollectionRunSupport runSupport(
            SaveTransitDataPort saveTransitDataPort,
            Long runId,
            Long... additionalRunIds
    ) {
        given(saveTransitDataPort.startCollectionRun(any(CollectionApiType.class), anyString(), anyString()))
                .willReturn(runId, additionalRunIds);
        return new CollectionRunSupport(saveTransitDataPort);
    }

    private static LoadTransitDataPort.RouteReference route(String sourceRouteId, String routeNo) {
        return new LoadTransitDataPort.RouteReference("38010", sourceRouteId, routeNo, 20, 20, 20, "0000", "0000", null);
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
