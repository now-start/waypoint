package org.nowstart.waypoint.application.port.out;

import java.time.Instant;
import java.util.List;

public interface LoadTagoLocationPort {

    List<TagoBusLocation> loadBusLocations(String cityCode, String sourceRouteId);

    record TagoBusLocation(
            String sourceRouteId,
            String routeNo,
            String vehicleNo,
            String sourceNodeId,
            Integer nodeOrder,
            Double gpsLatitude,
            Double gpsLongitude,
            Instant collectedAt
    ) {
    }
}
