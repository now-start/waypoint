package org.nowstart.waypoint.adapter.out.persistence.repository;

import org.nowstart.waypoint.adapter.out.persistence.entity.RouteStopEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;

public interface RouteStopJpaRepository extends JpaRepository<RouteStopEntity, RouteStopEntity.Id> {

    List<RouteStopEntity> findAllByCityCodeAndSourceRouteIdAndNodeOrderIn(
            String cityCode,
            String sourceRouteId,
            Collection<Integer> nodeOrders
    );

    @Query("""
            select route.sourceRouteId as sourceRouteId,
                   route.routeNo as routeNo,
                   route.routeType as routeType,
                   route.startNodeName as startNodeName,
                   route.endNodeName as endNodeName,
                   stop.sourceNodeId as sourceNodeId,
                   stop.nodeName as nodeName,
                   routeStop.nodeOrder as nodeOrder,
                   stop.gpsLatitude as gpsLatitude,
                   stop.gpsLongitude as gpsLongitude,
                   stop.lastArrivalCollectedAt as lastArrivalCollectedAt
            from RouteStopEntity routeStop
            join routeStop.busRoute route
            join routeStop.busStop stop
            where routeStop.cityCode = :cityCode
              and stop.gpsLatitude is not null
              and stop.gpsLongitude is not null
            order by route.routeNo asc, route.sourceRouteId asc, routeStop.nodeOrder asc
            """)
    List<RoutePathStopView> findMapPathStopsByCityCode(@Param("cityCode") String cityCode, Pageable pageable);

    interface RoutePathStopView {

        String getSourceRouteId();

        String getRouteNo();

        String getRouteType();

        String getStartNodeName();

        String getEndNodeName();

        String getSourceNodeId();

        String getNodeName();

        Integer getNodeOrder();

        Double getGpsLatitude();

        Double getGpsLongitude();

        Instant getLastArrivalCollectedAt();
    }
}
