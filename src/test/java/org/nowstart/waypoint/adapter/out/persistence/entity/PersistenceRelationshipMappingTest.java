package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.BDDAssertions.then;

class PersistenceRelationshipMappingTest {

    @Test
    @DisplayName("노선 경유 정류소는 노선과 정류소를 조회 전용 지연 로딩 연관관계로 가진다")
    void routeStopRelationships() throws NoSuchFieldException {
        // given: 노선 경유 정류소 엔티티
        Class<RouteStopEntity> entityType = RouteStopEntity.class;

        // when & then: 내부 ID 컬럼은 유지하고 조회 전용 연관관계를 확인한다
        thenReadOnlyManyToOne(entityType, "busRoute", "bus_route_id", false);
        thenReadOnlyManyToOne(entityType, "busStop", "bus_stop_id", false);
    }

    @Test
    @DisplayName("버스 위치 스냅샷은 노선을 조회 전용 지연 로딩 연관관계로 가진다")
    void locationSnapshotRelationship() throws NoSuchFieldException {
        // given: 버스 위치 스냅샷 엔티티
        Class<BusLocationSnapshotEntity> entityType = BusLocationSnapshotEntity.class;

        // when & then: 기준 노선 매칭 실패를 허용하기 위해 nullable 연관관계를 확인한다
        thenReadOnlyManyToOne(entityType, "busRoute", "bus_route_id", true);
    }

    @Test
    @DisplayName("버스 도착 스냅샷은 노선과 정류소를 조회 전용 지연 로딩 연관관계로 가진다")
    void arrivalSnapshotRelationships() throws NoSuchFieldException {
        // given: 버스 도착 스냅샷 엔티티
        Class<BusArrivalSnapshotEntity> entityType = BusArrivalSnapshotEntity.class;

        // when & then: 기준 데이터 매칭 실패를 허용하기 위해 nullable 연관관계를 확인한다
        thenReadOnlyManyToOne(entityType, "busRoute", "bus_route_id", true);
        thenReadOnlyManyToOne(entityType, "busStop", "bus_stop_id", true);
    }

    private void thenReadOnlyManyToOne(
            Class<?> entityType,
            String fieldName,
            String joinColumnName,
            boolean nullable
    ) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        then(manyToOne).isNotNull();
        then(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        then(joinColumn).isNotNull();
        then(joinColumn.name()).isEqualTo(joinColumnName);
        then(joinColumn.insertable()).isFalse();
        then(joinColumn.updatable()).isFalse();
        then(joinColumn.nullable()).isEqualTo(nullable);
    }
}
