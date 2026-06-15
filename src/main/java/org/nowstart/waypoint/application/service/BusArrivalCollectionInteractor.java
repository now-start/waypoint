package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
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

            int concurrency = collectionProperties.arrivalConcurrency();
            log.info(
                    "Starting TAGO bus arrival fetch. cityCode={}, stopCount={}, concurrency={}",
                    cityCode,
                    stops.size(),
                    concurrency
            );
            long fetchStartedAt = System.nanoTime();
            List<ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.StopReference,
                    List<LoadTagoArrivalPort.TagoBusArrival>>> results = ConcurrentCollectionSupport.execute(
                    "bus-arrival",
                    stops,
                    concurrency,
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
                    if (failureCount <= FAILURE_MESSAGE_LIMIT) {
                        logStopFailure(result);
                    } else if (failureCount == FAILURE_MESSAGE_LIMIT + 1) {
                        log.warn("Suppressing further TAGO bus arrival fetch failure logs.");
                    }
                } else {
                    arrivals.addAll(result.value());
                }
            }
            log.info(
                    "Finished TAGO bus arrival fetch. cityCode={}, stopCount={}, arrivalRows={}, failures={}, durationMs={}",
                    cityCode,
                    stops.size(),
                    arrivals.size(),
                    failureCount,
                    elapsedMillis(fetchStartedAt)
            );

            long saveStartedAt = System.nanoTime();
            int rowCount = saveTransitDataPort.saveArrivalSnapshots(cityCode, arrivals);
            log.info(
                    "Saved TAGO bus arrival snapshots. cityCode={}, rows={}, durationMs={}",
                    cityCode,
                    rowCount,
                    elapsedMillis(saveStartedAt)
            );

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

    private static void logStopFailure(
            ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.StopReference,
                    List<LoadTagoArrivalPort.TagoBusArrival>> result
    ) {
        log.warn(
                "TAGO bus arrival fetch failed. sourceNodeId={}, errorType={}, detail={}",
                result.source().sourceNodeId(),
                result.failure().getClass().getSimpleName(),
                CollectionFailureMessages.describe(result.failure())
        );
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
