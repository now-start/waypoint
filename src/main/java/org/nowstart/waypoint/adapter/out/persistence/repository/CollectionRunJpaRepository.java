package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.CollectionRunEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRunJpaRepository extends JpaRepository<CollectionRunEntity, Long> {

    List<CollectionRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
