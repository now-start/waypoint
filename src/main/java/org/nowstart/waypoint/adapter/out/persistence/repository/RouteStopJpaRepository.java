package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.RouteStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteStopJpaRepository extends JpaRepository<RouteStopEntity, Long> {

    Optional<RouteStopEntity> findByBusRouteIdAndNodeOrder(Long busRouteId, int nodeOrder);
}
