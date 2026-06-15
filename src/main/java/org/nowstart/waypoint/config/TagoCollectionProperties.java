package org.nowstart.waypoint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waypoint.tago.collection")
public record TagoCollectionProperties(
        int referenceDataConcurrency,
        int locationConcurrency,
        int arrivalConcurrency
) {

    private static final int DEFAULT_CONCURRENCY = 4;
    private static final int MAX_CONCURRENCY = 8;

    public TagoCollectionProperties {
        referenceDataConcurrency = boundedConcurrency(referenceDataConcurrency);
        locationConcurrency = boundedConcurrency(locationConcurrency);
        arrivalConcurrency = boundedConcurrency(arrivalConcurrency);
    }

    private static int boundedConcurrency(int value) {
        return value > 0 ? Math.min(value, MAX_CONCURRENCY) : DEFAULT_CONCURRENCY;
    }
}
