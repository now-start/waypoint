package org.nowstart.waypoint.application.port.in;

import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;

import java.time.Instant;
import java.util.List;

public interface QueryCollectionStatusUseCase {

    CollectionStatusView getStatus();

    List<CollectionRunView> getRecentRuns(int limit);

    record CollectionStatusView(
            long routeCount,
            long stopCount,
            long routeStopCount,
            long locationSnapshotCount,
            long arrivalSnapshotCount,
            Instant latestLocationCollectedAt,
            Instant latestArrivalCollectedAt,
            List<CollectionRunView> recentRuns
    ) {
    }

    record CollectionRunView(
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
