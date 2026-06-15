package org.nowstart.waypoint.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.adapter.out.persistence.entity.BusRouteEntity;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusRouteJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.BusStopJpaRepository;
import org.nowstart.waypoint.adapter.out.persistence.repository.RouteStopJpaRepository;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
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
}
