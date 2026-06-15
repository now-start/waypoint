package org.nowstart.waypoint.application.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class LocationCollectionAttemptRegistry {

    private final Map<RouteKey, Instant> lastAttemptedAtByRoute = new ConcurrentHashMap<>();

    Instant lastAttemptedAt(String cityCode, String sourceRouteId) {
        return lastAttemptedAtByRoute.get(new RouteKey(cityCode, sourceRouteId));
    }

    void markAttempted(String cityCode, Collection<String> sourceRouteIds, Instant attemptedAt) {
        sourceRouteIds.stream()
                .map(sourceRouteId -> new RouteKey(cityCode, sourceRouteId))
                .forEach(routeKey -> lastAttemptedAtByRoute.merge(routeKey, attemptedAt, LocationCollectionAttemptRegistry::latest));
    }

    private static Instant latest(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private record RouteKey(
            String cityCode,
            String sourceRouteId
    ) {
    }
}
