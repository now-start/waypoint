package org.nowstart.waypoint.domain.transit;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public final class RouteLocationCollectionPolicy {

    private static final Duration VERY_SHORT_HEADWAY_INTERVAL = Duration.ofMinutes(8);
    private static final Duration SHORT_HEADWAY_INTERVAL = Duration.ofMinutes(12);
    private static final Duration NORMAL_HEADWAY_INTERVAL = Duration.ofMinutes(20);
    private static final Duration LONG_HEADWAY_INTERVAL = Duration.ofMinutes(30);

    private RouteLocationCollectionPolicy() {
    }

    public static Selection selectDueRoutes(
            List<RouteCollectionCandidate> routes,
            Instant now,
            ZoneId zone
    ) {
        LocalTime localTime = LocalTime.ofInstant(now, zone);
        DayOfWeek dayOfWeek = now.atZone(zone).getDayOfWeek();
        List<RouteCollectionCandidate> activeRoutes = routes.stream()
                .filter(route -> RouteOperationWindow.isActive(route, localTime))
                .toList();
        List<String> dueSourceRouteIds = activeRoutes.stream()
                .filter(route -> isDue(route, now, dayOfWeek))
                .map(RouteCollectionCandidate::sourceRouteId)
                .toList();
        return new Selection(
                dueSourceRouteIds,
                routes.size() - activeRoutes.size(),
                activeRoutes.size() - dueSourceRouteIds.size()
        );
    }

    public static boolean isDue(RouteCollectionCandidate route, Instant now, DayOfWeek dayOfWeek) {
        Instant lastLocationAttemptedAt = route.lastLocationAttemptedAt();
        if (lastLocationAttemptedAt == null) {
            return true;
        }
        return !lastLocationAttemptedAt.plus(collectionInterval(route, dayOfWeek)).isAfter(now);
    }

    public static Duration collectionInterval(RouteCollectionCandidate route, DayOfWeek dayOfWeek) {
        Integer headwayMinutes = headwayMinutes(route, dayOfWeek);
        if (headwayMinutes == null) {
            return LONG_HEADWAY_INTERVAL;
        }
        if (headwayMinutes <= 15) {
            return VERY_SHORT_HEADWAY_INTERVAL;
        }
        if (headwayMinutes <= 30) {
            return SHORT_HEADWAY_INTERVAL;
        }
        if (headwayMinutes <= 60) {
            return NORMAL_HEADWAY_INTERVAL;
        }
        return LONG_HEADWAY_INTERVAL;
    }

    private static Integer headwayMinutes(RouteCollectionCandidate route, DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SATURDAY -> firstPositive(route.saturdayIntervalMinutes(), route.weekdayIntervalMinutes());
            case SUNDAY -> firstPositive(route.sundayIntervalMinutes(), route.weekdayIntervalMinutes());
            default -> firstPositive(route.weekdayIntervalMinutes(), route.saturdayIntervalMinutes(), route.sundayIntervalMinutes());
        };
    }

    private static Integer firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    public record Selection(
            List<String> dueSourceRouteIds,
            int skippedInactiveRoutes,
            int skippedNotDueRoutes
    ) {
    }
}
