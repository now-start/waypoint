package org.nowstart.waypoint.application.port.out;

import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadTransitDataPort {

    List<RouteReference> loadRoutes(String cityCode);

    List<StopReference> loadStops(String cityCode);

    Optional<Instant> latestLocationCollectedAt();

    Optional<Instant> latestArrivalCollectedAt();

    CollectionStatusSnapshot loadCollectionStatus(int recentRunLimit);

    List<CollectionRunSnapshot> loadRecentRuns(int limit);

    record RouteReference(
            String cityCode,
            String sourceRouteId,
            String routeNo
    ) {
    }

    record StopReference(
            String cityCode,
            String sourceNodeId,
            String nodeName,
            Instant lastArrivalCollectedAt
    ) {
    }

    record CollectionStatusSnapshot(
            long routeCount,
            long stopCount,
            long routeStopCount,
            long locationSnapshotCount,
            long arrivalSnapshotCount,
            Instant latestLocationCollectedAt,
            Instant latestArrivalCollectedAt,
            List<CollectionRunSnapshot> recentRuns
    ) {
    }

    record CollectionRunSnapshot(
            Long id,
            CollectionApiType apiType,
            CollectionStatus status,
            String requestKey,
            int rowCount,
            String resultCode,
            String resultMessage,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
