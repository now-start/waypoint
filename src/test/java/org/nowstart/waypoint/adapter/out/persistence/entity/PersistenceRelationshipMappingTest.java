package org.nowstart.waypoint.adapter.out.persistence.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

class PersistenceRelationshipMappingTest {

    @Test
    @DisplayName("기준정보는 TAGO 원본 키를 기본키로 사용한다")
    void referenceDataUsesSourceKeysAsPrimaryKeys() throws NoSuchFieldException {
        thenIdClass(BusRouteEntity.class, BusRouteEntity.Id.class);
        thenId(BusRouteEntity.class, "cityCode");
        thenId(BusRouteEntity.class, "sourceRouteId");

        thenIdClass(BusStopEntity.class, BusStopEntity.Id.class);
        thenId(BusStopEntity.class, "cityCode");
        thenId(BusStopEntity.class, "sourceNodeId");

        // given: 노선 경유 정류소 엔티티
        Class<RouteStopEntity> entityType = RouteStopEntity.class;

        // when & then: 노선 경유 정류소도 노선 원본 ID와 순번을 실제 PK로 사용한다
        thenIdClass(entityType, RouteStopEntity.Id.class);
        thenId(entityType, "cityCode");
        thenId(entityType, "sourceRouteId");
        thenId(entityType, "nodeOrder");
    }

    @Test
    @DisplayName("노선 경유 정류소는 natural key 기반 조회 전용 연관관계를 가진다")
    void routeStopRelationshipsUseNaturalKeys() throws NoSuchFieldException {
        thenReadOnlyManyToOne(
                RouteStopEntity.class,
                "busRoute",
                List.of("city_code", "source_route_id")
        );
        thenReadOnlyManyToOne(
                RouteStopEntity.class,
                "busStop",
                List.of("city_code", "source_node_id")
        );
    }

    @Test
    @DisplayName("스냅샷은 기준정보 내부 ID 대신 TAGO 원본 키를 보존한다")
    void snapshotsKeepSourceKeys() throws NoSuchFieldException {
        // given: 버스 위치 스냅샷 엔티티
        Class<BusLocationSnapshotEntity> locationType = BusLocationSnapshotEntity.class;

        // when & then: 기준 노선 매칭 실패와 무관하게 원본 키로 스냅샷을 보존한다
        locationType.getDeclaredField("cityCode");
        locationType.getDeclaredField("sourceRouteId");
        thenAssignedId(locationType);
        then(locationType.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("busRouteId", "busRoute");

        Class<BusArrivalSnapshotEntity> arrivalType = BusArrivalSnapshotEntity.class;
        arrivalType.getDeclaredField("cityCode");
        arrivalType.getDeclaredField("sourceNodeId");
        arrivalType.getDeclaredField("sourceRouteId");
        thenAssignedId(arrivalType);
        then(arrivalType.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("busStopId", "busStop", "busRouteId", "busRoute");
    }

    private void thenIdClass(Class<?> entityType, Class<?> idType) {
        IdClass idClass = entityType.getAnnotation(IdClass.class);

        then(idClass).isNotNull();
        then(idClass.value()).isEqualTo(idType);
    }

    private void thenId(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);

        then(field.getAnnotation(Id.class)).isNotNull();
    }

    private void thenAssignedId(Class<?> entityType) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField("id");

        then(field.getAnnotation(Id.class)).isNotNull();
        then(field.getAnnotation(GeneratedValue.class)).isNull();
    }

    private void thenReadOnlyManyToOne(
            Class<?> entityType,
            String fieldName,
            List<String> joinColumnNames
    ) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        JoinColumns joinColumns = field.getAnnotation(JoinColumns.class);

        then(manyToOne).isNotNull();
        then(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        then(joinColumns).isNotNull();
        then(joinColumns.value())
                .extracting(JoinColumn::name)
                .containsExactlyElementsOf(joinColumnNames);
        then(joinColumns.value())
                .allSatisfy(joinColumn -> {
                    then(joinColumn.insertable()).isFalse();
                    then(joinColumn.updatable()).isFalse();
                    then(joinColumn.nullable()).isFalse();
                });
    }
}
