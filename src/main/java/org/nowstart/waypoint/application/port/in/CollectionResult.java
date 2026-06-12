package org.nowstart.waypoint.application.port.in;

import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;

import java.time.Instant;

public record CollectionResult(
        CollectionApiType apiType,
        CollectionStatus status,
        int rowCount,
        int failureCount,
        String message,
        Instant startedAt,
        Instant finishedAt
) {
}
