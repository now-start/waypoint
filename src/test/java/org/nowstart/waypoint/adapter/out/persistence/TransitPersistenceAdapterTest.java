package org.nowstart.waypoint.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nowstart.waypoint.adapter.out.persistence.entity.BusRouteEntity;
import org.nowstart.waypoint.adapter.out.persistence.entity.BusStopEntity;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusArrivalSnapshotJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusLocationSnapshotJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusRouteJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusStopJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.CollectionRunJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.RouteStopJpaRepository;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransitPersistenceAdapterTest {

    @Mock
    private BusRouteJpaRepository busRouteRepository;

    @Mock
    private BusStopJpaRepository busStopRepository;

    @Mock
    private RouteStopJpaRepository routeStopRepository;

    @Mock
    private BusLocationSnapshotJpaRepository locationSnapshotRepository;

    @Mock
    private BusArrivalSnapshotJpaRepository arrivalSnapshotRepository;

    @Mock
    private CollectionRunJpaRepository collectionRunRepository;

    @Test
    @DisplayName("노선 저장은 기존 노선을 배치 조회하고 JPA 일괄 저장한다")
    void saveRoutesUsesBatchLookup() {
        // given: 두 노선을 저장하는 영속성 어댑터
        given(busRouteRepository.findAllByCityCodeAndSourceRouteIdIn(eq("38010"), anySourceRouteIds()))
                .willReturn(List.of());
        TransitPersistenceAdapter adapter = new TransitPersistenceAdapter(
                busRouteRepository,
                busStopRepository,
                routeStopRepository,
                locationSnapshotRepository,
                arrivalSnapshotRepository,
                collectionRunRepository
        );

        // when: 노선을 저장한다
        int savedCount = adapter.saveRoutes("38010", List.of(
                route("CWB101", "101"),
                route("CWB102", "102")
        ));

        // then: 건별 조회 대신 배치 조회와 JPA 일괄 저장을 사용한다
        assertThat(savedCount).isEqualTo(2);
        then(busRouteRepository).should().findAllByCityCodeAndSourceRouteIdIn(eq("38010"), anySourceRouteIds());
        then(busRouteRepository).should().saveAll(any());
    }

    @Test
    @DisplayName("노선 경유 정류소 저장은 정류장과 순번 매핑을 배치 조회한다")
    void saveRouteStopsUsesBatchLookups() {
        // given: 두 경유 정류소를 저장하는 영속성 어댑터
        BusStopEntity firstStop = busStop("CWS001");
        BusStopEntity secondStop = busStop("CWS002");
        given(busRouteRepository.existsByCityCodeAndSourceRouteId("38010", "CWB101")).willReturn(true);
        given(busStopRepository.findAllByCityCodeAndSourceNodeIdIn(eq("38010"), anySourceNodeIds()))
                .willReturn(List.of());
        given(busStopRepository.saveAll(any()))
                .willReturn(List.of(firstStop, secondStop));
        given(routeStopRepository.findAllByCityCodeAndSourceRouteIdAndNodeOrderIn(eq("38010"), eq("CWB101"), anyNodeOrders()))
                .willReturn(List.of());
        TransitPersistenceAdapter adapter = new TransitPersistenceAdapter(
                busRouteRepository,
                busStopRepository,
                routeStopRepository,
                locationSnapshotRepository,
                arrivalSnapshotRepository,
                collectionRunRepository
        );

        // when: 노선 경유 정류소를 저장한다
        int savedCount = adapter.saveRouteStops("38010", "CWB101", List.of(
                routeStop("CWS001", 1),
                routeStop("CWS002", 2)
        ));

        // then: 건별 조회 대신 배치 조회와 일괄 저장을 사용한다
        assertThat(savedCount).isEqualTo(2);
        then(busStopRepository).should().findAllByCityCodeAndSourceNodeIdIn(eq("38010"), anySourceNodeIds());
        then(routeStopRepository).should()
                .findAllByCityCodeAndSourceRouteIdAndNodeOrderIn(eq("38010"), eq("CWB101"), anyNodeOrders());
        then(routeStopRepository).should().saveAll(any());
    }

    @Test
    @DisplayName("위치 스냅샷 저장은 JPA 일괄 저장을 한 번 사용한다")
    void saveLocationSnapshotsUsesJpaBatchSave() {
        // given: 두 위치 스냅샷을 저장하는 영속성 어댑터
        TransitPersistenceAdapter adapter = new TransitPersistenceAdapter(
                busRouteRepository,
                busStopRepository,
                routeStopRepository,
                locationSnapshotRepository,
                arrivalSnapshotRepository,
                collectionRunRepository
        );

        // when: 위치 스냅샷을 저장한다
        int savedCount = adapter.saveLocationSnapshots("38010", List.of(
                location("CWB101", "경남71자1001"),
                location("CWB101", "경남71자1002")
        ));

        // then: 호출별 저장 대신 한 번의 JPA 일괄 저장을 사용한다
        assertThat(savedCount).isEqualTo(2);
        then(locationSnapshotRepository).should().saveAll(any());
    }

    @Test
    @DisplayName("노선 조회는 배차간격과 마지막 위치 수집 성공 시각을 포함한다")
    void loadRoutesIncludesHeadwaysAndLastLocationCollectedAt() {
        // given: 배차간격이 저장된 노선과 해당 노선의 최신 위치 스냅샷이 있다
        BusRouteEntity route = mock(BusRouteEntity.class);
        BusLocationSnapshotJpaRepository.RouteLatestCollectedAt latestCollectedAt =
                mock(BusLocationSnapshotJpaRepository.RouteLatestCollectedAt.class);
        Instant lastLocationCollectedAt = Instant.parse("2026-06-13T00:00:00Z");
        given(route.getCityCode()).willReturn("38010");
        given(route.getSourceRouteId()).willReturn("CWB101");
        given(route.getRouteNo()).willReturn("101");
        given(route.getWeekdayIntervalMinutes()).willReturn(20);
        given(route.getSaturdayIntervalMinutes()).willReturn(30);
        given(route.getSundayIntervalMinutes()).willReturn(40);
        given(route.getFirstVehicleTime()).willReturn("0500");
        given(route.getLastVehicleTime()).willReturn("2330");
        given(latestCollectedAt.getSourceRouteId()).willReturn("CWB101");
        given(latestCollectedAt.getCollectedAt()).willReturn(lastLocationCollectedAt);
        given(busRouteRepository.findAllByCityCodeOrderByRouteNoAsc("38010")).willReturn(List.of(route));
        given(locationSnapshotRepository.findLatestCollectedAtByCityCodeAndSourceRouteIdIn(eq("38010"), anySourceRouteIds()))
                .willReturn(List.of(latestCollectedAt));
        TransitPersistenceAdapter adapter = new TransitPersistenceAdapter(
                busRouteRepository,
                busStopRepository,
                routeStopRepository,
                locationSnapshotRepository,
                arrivalSnapshotRepository,
                collectionRunRepository
        );

        // when: 노선 조회 read model을 만든다
        List<LoadTransitDataPort.RouteReference> routes = adapter.loadRoutes("38010");

        // then: due 판단에 필요한 필드를 포함한다
        assertThat(routes).containsExactly(new LoadTransitDataPort.RouteReference(
                "38010",
                "CWB101",
                "101",
                20,
                30,
                40,
                "0500",
                "2330",
                lastLocationCollectedAt
        ));
    }

    @Test
    @DisplayName("도착 스냅샷 저장은 정류장을 배치 조회해 마지막 수집 시각을 갱신한다")
    void saveArrivalSnapshotsUsesBatchStopLookup() {
        // given: 도착 스냅샷이 수집된 정류장이 있다
        BusStopEntity stop = busStop("CWS001");
        given(busStopRepository.findAllByCityCodeAndSourceNodeIdIn(eq("38010"), anySourceNodeIds()))
                .willReturn(List.of(stop));
        TransitPersistenceAdapter adapter = new TransitPersistenceAdapter(
                busRouteRepository,
                busStopRepository,
                routeStopRepository,
                locationSnapshotRepository,
                arrivalSnapshotRepository,
                collectionRunRepository
        );
        Instant collectedAt = Instant.parse("2026-06-13T00:00:00Z");

        // when: 도착 스냅샷을 저장한다
        int savedCount = adapter.saveArrivalSnapshots("38010", List.of(arrival("CWS001", collectedAt)));

        // then: 스냅샷은 일괄 저장하고, 정류장은 단건 조회 없이 배치 조회로 갱신한다
        assertThat(savedCount).isEqualTo(1);
        then(arrivalSnapshotRepository).should().saveAll(any());
        then(busStopRepository).should().findAllByCityCodeAndSourceNodeIdIn(eq("38010"), anySourceNodeIds());
        verify(stop).markArrivalCollected(collectedAt);
    }

    private static Collection<String> anySourceNodeIds() {
        return any();
    }

    private static Collection<String> anySourceRouteIds() {
        return any();
    }

    private static Collection<Integer> anyNodeOrders() {
        return any();
    }

    private static LoadTagoRoutePort.TagoRoute route(String sourceRouteId, String routeNo) {
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

    private static LoadTagoRoutePort.TagoRouteStop routeStop(String sourceNodeId, int nodeOrder) {
        return new LoadTagoRoutePort.TagoRouteStop(
                sourceNodeId,
                "100",
                "정류장",
                nodeOrder,
                35.1,
                128.1
        );
    }

    private static LoadTagoLocationPort.TagoBusLocation location(String sourceRouteId, String vehicleNo) {
        return new LoadTagoLocationPort.TagoBusLocation(
                sourceRouteId,
                "101",
                vehicleNo,
                "CWS001",
                1,
                35.1,
                128.1,
                Instant.parse("2026-06-13T00:00:00Z")
        );
    }

    private static LoadTagoArrivalPort.TagoBusArrival arrival(String sourceNodeId, Instant collectedAt) {
        return new LoadTagoArrivalPort.TagoBusArrival(
                sourceNodeId,
                "정류장",
                "CWB101",
                "101",
                "간선",
                3,
                5,
                collectedAt.plusSeconds(300),
                "일반",
                collectedAt
        );
    }

    private static BusStopEntity busStop(String sourceNodeId) {
        BusStopEntity stop = mock(BusStopEntity.class);
        given(stop.getSourceNodeId()).willReturn(sourceNodeId);
        return stop;
    }
}
