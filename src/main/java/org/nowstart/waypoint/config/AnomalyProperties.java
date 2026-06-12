package org.nowstart.waypoint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "waypoint.anomaly")
public record AnomalyProperties(
        Duration headwayWide,
        Duration headwayNarrow,
        Duration locationStale
) {

    public AnomalyProperties {
        headwayWide = headwayWide == null ? Duration.ofMinutes(30) : headwayWide;
        headwayNarrow = headwayNarrow == null ? Duration.ofMinutes(3) : headwayNarrow;
        locationStale = locationStale == null ? Duration.ofMinutes(5) : locationStale;
    }
}
