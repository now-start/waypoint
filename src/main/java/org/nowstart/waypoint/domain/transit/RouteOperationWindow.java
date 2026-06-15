package org.nowstart.waypoint.domain.transit;

import java.time.LocalTime;
import java.util.Optional;

final class RouteOperationWindow {

    private static final LocalTime DEFAULT_FIRST_VEHICLE_TIME = LocalTime.of(5, 0);
    private static final LocalTime DEFAULT_LAST_VEHICLE_TIME = LocalTime.of(23, 30);

    private RouteOperationWindow() {
    }

    static boolean isActive(RouteCollectionCandidate route, LocalTime now) {
        Optional<LocalTime> parsedFirstVehicleTime = parse(route.firstVehicleTime());
        Optional<LocalTime> parsedLastVehicleTime = parse(route.lastVehicleTime());
        LocalTime firstVehicleTime = DEFAULT_FIRST_VEHICLE_TIME;
        LocalTime lastVehicleTime = DEFAULT_LAST_VEHICLE_TIME;
        if (parsedFirstVehicleTime.isPresent() && parsedLastVehicleTime.isPresent()) {
            firstVehicleTime = parsedFirstVehicleTime.orElseThrow();
            lastVehicleTime = parsedLastVehicleTime.orElseThrow();
        }
        return contains(now, firstVehicleTime, lastVehicleTime);
    }

    private static boolean contains(LocalTime now, LocalTime firstVehicleTime, LocalTime lastVehicleTime) {
        if (firstVehicleTime.equals(lastVehicleTime)) {
            return true;
        }
        if (firstVehicleTime.isBefore(lastVehicleTime)) {
            return !now.isBefore(firstVehicleTime) && !now.isAfter(lastVehicleTime);
        }
        return !now.isBefore(firstVehicleTime) || !now.isAfter(lastVehicleTime);
    }

    private static Optional<LocalTime> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 3) {
            digits = "0" + digits;
        }
        if (digits.length() != 4) {
            return Optional.empty();
        }
        int hour = Integer.parseInt(digits.substring(0, 2));
        int minute = Integer.parseInt(digits.substring(2, 4));
        if (hour == 24 && minute == 0) {
            return Optional.of(LocalTime.MAX);
        }
        if (hour > 23 || minute > 59) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hour, minute));
    }
}
