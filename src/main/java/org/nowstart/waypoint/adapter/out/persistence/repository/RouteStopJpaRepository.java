package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.RouteStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RouteStopJpaRepository extends JpaRepository<RouteStopEntity, RouteStopEntity.Id> {

    List<RouteStopEntity> findAllByCityCodeAndSourceRouteIdAndNodeOrderIn(
            String cityCode,
            String sourceRouteId,
            Collection<Integer> nodeOrders
    );
}
