package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.CollectBusArrivalUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.config.TagoCollectionProperties;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusArrivalCollectionInteractor implements CollectBusArrivalUseCase {

    private static final int FAILURE_MESSAGE_LIMIT = 10;

    private final TagoCityCodeResolver cityCodeResolver;
    private final LoadTransitDataPort loadTransitDataPort;
    private final LoadTagoArrivalPort arrivalPort;
    private final SaveTransitDataPort saveTransitDataPort;
    private final CollectionRunSupport runSupport;
    private final TagoCollectionProperties collectionProperties;

    @Override
    public CollectionResult collectAllStops() {
        CollectionRunSupport.CollectionRun run = runSupport.start(
                CollectionApiType.BUS_ARRIVAL,
                "changwon-all-stop-arrivals",
                "{\"scope\":\"all-stops\"}"
        );
        try {
            String cityCode = cityCodeResolver.resolve();
            List<LoadTransitDataPort.StopReference> stops = loadTransitDataPort.loadStops(cityCode);
            if (stops.isEmpty()) {
                return runSupport.finish(run, CollectionStatus.EMPTY, 0, 0,
                        "수집된 정류장 기준 데이터가 없습니다.");
            }

            List<ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.StopReference,
                    List<LoadTagoArrivalPort.TagoBusArrival>>> results = ConcurrentCollectionSupport.execute(
                    stops,
                    collectionProperties.arrivalConcurrency(),
                    stop -> arrivalPort.loadArrivals(cityCode, stop.sourceNodeId()),
                    stop -> List.of()
            );

            List<LoadTagoArrivalPort.TagoBusArrival> arrivals = new ArrayList<>();
            int failureCount = 0;
            List<String> failedStopIds = new ArrayList<>();
            for (ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.StopReference,
                    List<LoadTagoArrivalPort.TagoBusArrival>> result : results) {
                if (result.failed()) {
                    failureCount++;
                    failedStopIds.add(result.source().sourceNodeId());
                } else {
                    arrivals.addAll(result.value());
                }
            }
            int rowCount = saveTransitDataPort.saveArrivalSnapshots(cityCode, arrivals);

            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    resultMessage(rowCount, failureCount, failedStopIds));
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private static String resultMessage(int rowCount, int failureCount, List<String> failedStopIds) {
        String message = "arrivals=" + rowCount + ", stopFailures=" + failureCount;
        if (failedStopIds.isEmpty()) {
            return message;
        }
        int limit = Math.min(FAILURE_MESSAGE_LIMIT, failedStopIds.size());
        String suffix = failedStopIds.size() > limit ? ", ..." : "";
        return message + ", failedStopIds=" + failedStopIds.subList(0, limit) + suffix;
    }

    private static CollectionStatus status(int rowCount, int failureCount) {
        if (rowCount == 0 && failureCount == 0) {
            return CollectionStatus.EMPTY;
        }
        return failureCount > 0 ? CollectionStatus.PARTIAL : CollectionStatus.SUCCESS;
    }
}
