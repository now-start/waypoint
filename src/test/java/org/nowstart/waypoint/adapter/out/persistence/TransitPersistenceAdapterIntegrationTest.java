package org.nowstart.waypoint.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.adapter.out.persistence.entity.BusRouteEntity;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusRouteJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusStopJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.RouteStopJpaRepository;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TransitPersistenceAdapterIntegrationTest.PersistenceTestConfiguration.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:transitdb;MODE=MariaDB;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.cloud.config.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false"
        }
)
class TransitPersistenceAdapterIntegrationTest {

    @Autowired
    private TransitPersistenceAdapter adapter;

    @Autowired
    private BusRouteJpaRepository busRouteRepository;

    @Autowired
    private BusStopJpaRepository busStopRepository;

    @Autowired
    private RouteStopJpaRepository routeStopRepository;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = BusRouteEntity.class)
    @EnableJpaRepositories(basePackageClasses = BusRouteJpaRepository.class)
    @Import(TransitPersistenceAdapter.class)
    static class PersistenceTestConfiguration {
    }

    @BeforeEach
    void cleanDatabase() {
        routeStopRepository.deleteAll();
        busStopRepository.deleteAll();
        busRouteRepository.deleteAll();
    }

    @Test
    @DisplayName("중복 노선 입력은 하나로 합쳐 JPA 일괄 저장한다")
    void saveRoutesCoalescesDuplicateSourceRouteIds() {
        // when: 같은 TAGO 노선 ID가 중복으로 들어온다
        int savedCount = adapter.saveRoutes("38010", List.of(
                route("CWB101", "101"),
                route("CWB101", "101-1")
        ));

        // then: unique constraint 오류 없이 마지막 값 기준으로 한 건만 저장한다
        List<BusRouteEntity> routes = busRouteRepository.findAll();
        assertThat(savedCount).isEqualTo(1);
        assertThat(routes).hasSize(1);
        assertThat(routes.getFirst().getRouteNo()).isEqualTo("101-1");
    }

    @Test
    @DisplayName("중복 정류장 입력은 정류장을 한 번만 저장하고 노선 순번은 유지한다")
    void saveRouteStopsCoalescesDuplicateStopsAndRouteOrders() {
        // given: 노선 기준정보가 저장되어 있다
        adapter.saveRoutes("38010", List.of(route("CWB101", "101")));

        // when: 같은 정류장이 여러 순번에 반복되고, 같은 순번도 중복으로 들어온다
        int firstCount = adapter.saveRouteStops("38010", "CWB101", List.of(
                routeStop("CWS001", 1),
                routeStop("CWS001", 2),
                routeStop("CWS002", 3),
                routeStop("CWS001", 1)
        ));
        int secondCount = adapter.saveRouteStops("38010", "CWB101", List.of(
                routeStop("CWS001", 1),
                routeStop("CWS001", 2),
                routeStop("CWS002", 3),
                routeStop("CWS001", 1)
        ));

        // then: 재수집해도 unique constraint 오류 없이 idempotent하게 반영한다
        assertThat(firstCount).isEqualTo(3);
        assertThat(secondCount).isEqualTo(3);
        assertThat(busStopRepository.findAll()).hasSize(2);
        assertThat(routeStopRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("지도 경로 조회는 좌표 있는 경유 정류소만 노선과 순번 순서로 반환한다")
    void loadRoutePathStopsReturnsOrderedCoordinateStops() {
        // given: 두 노선의 경유 정류소와 좌표 없는 정류소가 저장되어 있다
        String cityCode = "39000";
        adapter.saveRoutes(cityCode, List.of(route("CWB200", "200"), route("CWB100", "100")));
        adapter.saveRouteStops(cityCode, "CWB200", List.of(
                routeStop("CWS2002", 2, 35.12, 128.62),
                routeStop("CWS2001", 1, 35.11, 128.61)
        ));
        adapter.saveRouteStops(cityCode, "CWB100", List.of(
                routeStop("CWS1001", 1, 35.21, 128.71),
                routeStop("CWS1002", 2, null, null)
        ));

        // when: 지도 경로 정류소를 조회한다
        List<LoadTransitDataPort.RoutePathStopReference> rows = adapter.loadRoutePathStops(cityCode, 10);
        List<LoadTransitDataPort.RoutePathStopReference> limitedRows = adapter.loadRoutePathStops(cityCode, 2);

        // then: 좌표 없는 정류소는 제외하고 routeNo, sourceRouteId, nodeOrder 순서로 반환한다
        assertThat(rows).extracting(LoadTransitDataPort.RoutePathStopReference::sourceNodeId)
                .containsExactly("CWS1001", "CWS2001", "CWS2002");
        assertThat(rows.getFirst().routeNo()).isEqualTo("100");
        assertThat(rows.getFirst().gpsLatitude()).isEqualTo(35.21);
        assertThat(limitedRows).hasSize(2);
        assertThat(limitedRows).extracting(LoadTransitDataPort.RoutePathStopReference::sourceNodeId)
                .containsExactly("CWS1001", "CWS2001");
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
        return routeStop(sourceNodeId, nodeOrder, 35.1, 128.1);
    }

    private static LoadTagoRoutePort.TagoRouteStop routeStop(
            String sourceNodeId,
            int nodeOrder,
            Double latitude,
            Double longitude
    ) {
        return new LoadTagoRoutePort.TagoRouteStop(
                sourceNodeId,
                "100",
                "정류장",
                nodeOrder,
                latitude,
                longitude
        );
    }
}
