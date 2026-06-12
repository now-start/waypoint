package org.nowstart.waypoint.application.port.out;

import java.time.Instant;
import java.util.List;

public interface LoadTagoArrivalPort {

    List<TagoBusArrival> loadArrivals(String cityCode, String sourceNodeId);

    record TagoBusArrival(
            String sourceNodeId,
            String nodeName,
            String sourceRouteId,
            String routeNo,
            String routeType,
            Integer arrivalRemainingStationCount,
            Integer arrivalRemainingMinutes,
            Instant arrivalExpectedAt,
            String vehicleType,
            Instant collectedAt
    ) {
    }
}
