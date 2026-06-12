package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusStopJpaRepository extends JpaRepository<BusStopEntity, Long> {

    Optional<BusStopEntity> findByCityCodeAndSourceNodeId(String cityCode, String sourceNodeId);

    List<BusStopEntity> findAllByCityCodeAndSourceNodeIdIn(String cityCode, Collection<String> sourceNodeIds);
}
