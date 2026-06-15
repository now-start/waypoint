package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusArrivalSnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BusArrivalSnapshotJpaRepository extends JpaRepository<BusArrivalSnapshotEntity, String> {

    @Query("select max(snapshot.collectedAt) from BusArrivalSnapshotEntity snapshot")
    Optional<Instant> findLatestCollectedAt();

    @Query("""
            select snapshot.sourceRouteId as sourceRouteId,
                   snapshot.routeNo as routeNo,
                   snapshot.sourceNodeId as sourceNodeId,
                   snapshot.nodeName as nodeName,
                   snapshot.arrivalRemainingMinutes as arrivalRemainingMinutes,
                   snapshot.arrivalExpectedAt as arrivalExpectedAt,
                   snapshot.collectedAt as collectedAt
            from BusArrivalSnapshotEntity snapshot
            where snapshot.cityCode = :cityCode
              and snapshot.sourceRouteId is not null
              and snapshot.sourceNodeId is not null
              and snapshot.arrivalRemainingMinutes is not null
              and snapshot.collectedAt >= :since
            order by snapshot.collectedAt desc
            """)
    List<ArrivalSnapshotView> findRecentByCityCode(
            @Param("cityCode") String cityCode,
            @Param("since") Instant since,
            Pageable pageable
    );

    interface ArrivalSnapshotView {

        String getSourceRouteId();

        String getRouteNo();

        String getSourceNodeId();

        String getNodeName();

        Integer getArrivalRemainingMinutes();

        Instant getArrivalExpectedAt();

        Instant getCollectedAt();
    }
}
