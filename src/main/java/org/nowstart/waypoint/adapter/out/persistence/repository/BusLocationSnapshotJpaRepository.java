package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusLocationSnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusLocationSnapshotJpaRepository extends JpaRepository<BusLocationSnapshotEntity, String> {

    @Query("select max(snapshot.collectedAt) from BusLocationSnapshotEntity snapshot")
    Optional<Instant> findLatestCollectedAt();

    @Query("""
            select snapshot.sourceRouteId as sourceRouteId, max(snapshot.collectedAt) as collectedAt
            from BusLocationSnapshotEntity snapshot
            where snapshot.cityCode = :cityCode
              and snapshot.sourceRouteId in :sourceRouteIds
            group by snapshot.sourceRouteId
            """)
    List<RouteLatestCollectedAt> findLatestCollectedAtByCityCodeAndSourceRouteIdIn(
            @Param("cityCode") String cityCode,
            @Param("sourceRouteIds") Collection<String> sourceRouteIds
    );

    @Query("""
            select snapshot.sourceRouteId as sourceRouteId,
                   snapshot.routeNo as routeNo,
                   snapshot.vehicleNo as vehicleNo,
                   snapshot.sourceNodeId as sourceNodeId,
                   snapshot.nodeOrder as nodeOrder,
                   snapshot.gpsLatitude as gpsLatitude,
                   snapshot.gpsLongitude as gpsLongitude,
                   snapshot.collectedAt as collectedAt
            from BusLocationSnapshotEntity snapshot
            where snapshot.cityCode = :cityCode
              and snapshot.collectedAt >= :since
            order by snapshot.collectedAt desc
            """)
    List<LocationSnapshotView> findRecentByCityCode(
            @Param("cityCode") String cityCode,
            @Param("since") Instant since,
            Pageable pageable
    );

    interface RouteLatestCollectedAt {

        String getSourceRouteId();

        Instant getCollectedAt();
    }

    interface LocationSnapshotView {

        String getSourceRouteId();

        String getRouteNo();

        String getVehicleNo();

        String getSourceNodeId();

        Integer getNodeOrder();

        Double getGpsLatitude();

        Double getGpsLongitude();

        Instant getCollectedAt();
    }
}
