package org.nowstart.waypoint.config;

import org.nowstart.waypoint.application.port.out.ArrivalObservationSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "waypoint.collection")
public record CollectionProperties(
        boolean referenceDataOnStartup,
        boolean locationSchedulerEnabled,
        Duration locationFixedDelay,
        List<String> arrivalObservationSourceNodeIds
) implements ArrivalObservationSettings {

    public CollectionProperties {
        locationFixedDelay = locationFixedDelay == null ? Duration.ofMinutes(1) : locationFixedDelay;
        arrivalObservationSourceNodeIds = arrivalObservationSourceNodeIds == null
                ? List.of()
                : arrivalObservationSourceNodeIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
