package org.nowstart.waypoint.application.port.in;

import java.time.Instant;
import java.util.List;

public interface QueryTransitMapUseCase {

    TransitMapView getMap();

    record TransitMapView(
            Instant generatedAt,
            MapBounds bounds,
            MapSummary summary,
            List<MapRoute> routes,
            List<MapVehicle> vehicles
    ) {
    }

    record MapBounds(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude
    ) {
    }

    record MapSummary(
            int routeCount,
            int stopCount,
            int vehicleCount,
            int delayedVehicleCount
    ) {
    }

    record MapRoute(
            String sourceRouteId,
            String routeNo,
            String routeType,
            String startNodeName,
            String endNodeName,
            List<MapStop> stops
    ) {
    }

    record MapStop(
            String sourceNodeId,
            String nodeName,
            Integer nodeOrder,
            Double latitude,
            Double longitude,
            Instant lastArrivalCollectedAt
    ) {
    }

    record MapVehicle(
            String sourceRouteId,
            String routeNo,
            String vehicleNo,
            String sourceNodeId,
            Integer nodeOrder,
            Double latitude,
            Double longitude,
            Instant collectedAt,
            String freshness
    ) {
    }
}
