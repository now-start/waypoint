package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.BusRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BusRouteJpaRepository extends JpaRepository<BusRouteEntity, BusRouteEntity.Id> {

    boolean existsByCityCodeAndSourceRouteId(String cityCode, String sourceRouteId);

    List<BusRouteEntity> findAllByCityCodeOrderByRouteNoAsc(String cityCode);

    List<BusRouteEntity> findAllByCityCodeAndSourceRouteIdIn(String cityCode, Collection<String> sourceRouteIds);
}
