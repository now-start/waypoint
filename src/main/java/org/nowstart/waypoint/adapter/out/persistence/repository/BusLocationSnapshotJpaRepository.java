package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusLocationSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface BusLocationSnapshotJpaRepository extends JpaRepository<BusLocationSnapshotEntity, Long> {

    @Query("select max(snapshot.collectedAt) from BusLocationSnapshotEntity snapshot")
    Optional<Instant> findLatestCollectedAt();
}
