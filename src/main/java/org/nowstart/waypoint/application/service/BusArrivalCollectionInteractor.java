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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean collectionRunning = new AtomicBoolean(false);

    @Override
    public CollectionResult collectAllStops() {
        if (!collectionRunning.compareAndSet(false, true)) {
            String message = "이미 TAGO 버스 도착정보 수집이 진행 중입니다.";
            Instant now = Instant.now();
            return new CollectionResult(
                    CollectionApiType.BUS_ARRIVAL,
                    CollectionStatus.EMPTY,
                    0,
                    0,
                    message,
                    now,
                    now
            );
        }
        try {
            return collectLocked();
        } finally {
            collectionRunning.set(false);
        }
    }

    private CollectionResult collectLocked() {
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
            List<LoadTransitDataPort.StopReference> selectedStops = selectStops(stops, collectionProperties.arrivalMaxStopsPerRun());
            if (selectedStops.isEmpty()) {
                return runSupport.finish(run, CollectionStatus.EMPTY, 0, 0,
                        "도착정보 수집 대상 정류장이 없습니다.");
            }

            int concurrency = collectionProperties.arrivalConcurrency();
            log.info(
                    "Starting TAGO bus arrival fetch. cityCode={}, stopCount={}, selectedStopCount={}, concurrency={}",
                    cityCode,
                    stops.size(),
                    selectedStops.size(),
                    concurrency
            );
            long fetchStartedAt = System.nanoTime();
            List<ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.StopReference,
                    List<LoadTagoArrivalPort.TagoBusArrival>>> results = ConcurrentCollectionSupport.execute(
                    "bus-arrival",
                    selectedStops,
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
            Map<String, Instant> collectedAtByStop = collectedAtBySuccessfulStop(results);
            log.info(
                    "Finished TAGO bus arrival fetch. cityCode={}, stopCount={}, selectedStopCount={}, arrivalRows={}, failures={}, durationMs={}",
                    cityCode,
                    stops.size(),
                    selectedStops.size(),
                    arrivals.size(),
                    failureCount,
                    elapsedMillis(fetchStartedAt)
            );

            long saveStartedAt = System.nanoTime();
            int rowCount = saveTransitDataPort.saveArrivalSnapshots(cityCode, arrivals, collectedAtByStop);
            log.info(
                    "Saved TAGO bus arrival snapshots. cityCode={}, rows={}, durationMs={}",
                    cityCode,
                    rowCount,
                    elapsedMillis(saveStartedAt)
            );

            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    resultMessage(rowCount, failureCount, selectedStops.size(), stops.size(), failedStopIds));
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private static Map<String, Instant> collectedAtBySuccessfulStop(
            List<ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.StopReference,
                    List<LoadTagoArrivalPort.TagoBusArrival>>> results
    ) {
        Instant attemptedAt = Instant.now();
        Map<String, Instant> collectedAtByStop = new LinkedHashMap<>();
        for (ConcurrentCollectionSupport.TaskResult<
                LoadTransitDataPort.StopReference,
                List<LoadTagoArrivalPort.TagoBusArrival>> result : results) {
            if (result.failed() || result.source().sourceNodeId() == null) {
                continue;
            }
            Instant collectedAt = result.value().stream()
                    .map(LoadTagoArrivalPort.TagoBusArrival::collectedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(attemptedAt);
            collectedAtByStop.put(result.source().sourceNodeId(), collectedAt);
        }
        return collectedAtByStop;
    }

    private static List<LoadTransitDataPort.StopReference> selectStops(
            List<LoadTransitDataPort.StopReference> stops,
            int maxStops
    ) {
        return stops.stream()
                .sorted(Comparator
                        .comparing(
                                LoadTransitDataPort.StopReference::lastArrivalCollectedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                LoadTransitDataPort.StopReference::sourceNodeId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .limit(maxStops)
                .toList();
    }

    private static String resultMessage(
            int rowCount,
            int failureCount,
            int selectedStopCount,
            int totalStopCount,
            List<String> failedStopIds
    ) {
        String message = "arrivals=" + rowCount
                + ", stopFailures=" + failureCount
                + ", selectedStops=" + selectedStopCount
                + ", totalStops=" + totalStopCount;
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
