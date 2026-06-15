package org.nowstart.waypoint.domain.transit;

import java.time.Instant;

public record RouteCollectionCandidate(
        String sourceRouteId,
        Integer weekdayIntervalMinutes,
        Integer saturdayIntervalMinutes,
        Integer sundayIntervalMinutes,
        String firstVehicleTime,
        String lastVehicleTime,
        Instant lastLocationAttemptedAt
) {
}
