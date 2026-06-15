package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BusStopJpaRepository extends JpaRepository<BusStopEntity, BusStopEntity.Id> {

    List<BusStopEntity> findAllByCityCodeAndSourceNodeIdIn(String cityCode, Collection<String> sourceNodeIds);

    List<BusStopEntity> findAllByCityCodeOrderBySourceNodeIdAsc(String cityCode);
}
