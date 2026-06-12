package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.in.QueryCollectionStatusUseCase;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollectionStatusInteractor implements QueryCollectionStatusUseCase {

    private static final int DEFAULT_RECENT_RUN_LIMIT = 10;

    private final LoadTransitDataPort loadTransitDataPort;

    public CollectionStatusInteractor(LoadTransitDataPort loadTransitDataPort) {
        this.loadTransitDataPort = loadTransitDataPort;
    }

    @Override
    public CollectionStatusView getStatus() {
        LoadTransitDataPort.CollectionStatusSnapshot snapshot =
                loadTransitDataPort.loadCollectionStatus(DEFAULT_RECENT_RUN_LIMIT);
        return new CollectionStatusView(
                snapshot.routeCount(),
                snapshot.stopCount(),
                snapshot.routeStopCount(),
                snapshot.locationSnapshotCount(),
                snapshot.arrivalSnapshotCount(),
                snapshot.latestLocationCollectedAt(),
                snapshot.latestArrivalCollectedAt(),
                snapshot.recentRuns().stream()
                        .map(this::toRunView)
                        .toList()
        );
    }

    @Override
    public List<CollectionRunView> getRecentRuns(int limit) {
        return loadTransitDataPort.loadRecentRuns(limit).stream()
                .map(this::toRunView)
                .toList();
    }

    private CollectionRunView toRunView(LoadTransitDataPort.CollectionRunSnapshot run) {
        return new CollectionRunView(
                run.id(),
                run.apiType(),
                run.status(),
                run.requestKey(),
                run.rowCount(),
                run.resultCode(),
                run.resultMessage(),
                run.errorMessage(),
                run.startedAt(),
                run.finishedAt()
        );
    }
}
