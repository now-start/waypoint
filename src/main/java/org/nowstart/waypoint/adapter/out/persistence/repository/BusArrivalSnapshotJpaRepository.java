package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusArrivalSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface BusArrivalSnapshotJpaRepository extends JpaRepository<BusArrivalSnapshotEntity, Long> {

    @Query("select max(snapshot.collectedAt) from BusArrivalSnapshotEntity snapshot")
    Optional<Instant> findLatestCollectedAt();
}
