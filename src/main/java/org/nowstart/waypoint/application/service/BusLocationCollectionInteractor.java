package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.config.TagoCollectionProperties;
import org.nowstart.waypoint.domain.transit.RouteCollectionCandidate;
import org.nowstart.waypoint.domain.transit.RouteLocationCollectionPolicy;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusLocationCollectionInteractor implements CollectBusLocationUseCase {

    private static final int FAILURE_MESSAGE_LIMIT = 10;
    private static final ZoneId TAGO_ZONE = ZoneId.of("Asia/Seoul");

    private final TagoCityCodeResolver cityCodeResolver;
    private final LoadTransitDataPort loadTransitDataPort;
    private final LoadTagoLocationPort locationPort;
    private final SaveTransitDataPort saveTransitDataPort;
    private final CollectionRunSupport runSupport;
    private final TagoCollectionProperties collectionProperties;
    private final LocationCollectionAttemptRegistry attemptRegistry;
    private final AtomicBoolean collectionRunning = new AtomicBoolean(false);

    @Override
    public CollectionResult collect() {
        Instant startedAt = Instant.now();
        if (!collectionRunning.compareAndSet(false, true)) {
            String message = "이미 TAGO 버스 위치 수집이 진행 중입니다.";
            return new CollectionResult(
                    CollectionApiType.BUS_LOCATION,
                    CollectionStatus.EMPTY,
                    0,
                    0,
                    message,
                    startedAt,
                    Instant.now()
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
                CollectionApiType.BUS_LOCATION,
                "changwon-all-route-locations",
                "{\"scope\":\"all-routes\"}"
        );
        try {
            String cityCode = cityCodeResolver.resolve();
            List<LoadTransitDataPort.RouteReference> routes = loadTransitDataPort.loadRoutes(cityCode);
            if (routes.isEmpty()) {
                return runSupport.finish(run, CollectionStatus.EMPTY, 0, 0, "수집된 노선 기준 데이터가 없습니다.");
            }

            int concurrency = collectionProperties.locationConcurrency();
            Instant now = Instant.now();
            RouteLocationCollectionPolicy.Selection routeSelection = RouteLocationCollectionPolicy.selectDueRoutes(
                    routes.stream()
                            .map(route -> toRouteCollectionCandidate(cityCode, route))
                            .toList(),
                    now,
                    TAGO_ZONE
            );
            Set<String> dueRouteIds = new LinkedHashSet<>(routeSelection.dueSourceRouteIds());
            List<LoadTransitDataPort.RouteReference> dueRoutes = routes.stream()
                    .filter(route -> dueRouteIds.contains(route.sourceRouteId()))
                    .toList();
            if (dueRoutes.isEmpty()) {
                return runSupport.finish(
                        run,
                        CollectionStatus.EMPTY,
                        0,
                        0,
                        "수집 주기가 도래한 운행 노선이 없습니다."
                                + " skippedInactiveRoutes=" + routeSelection.skippedInactiveRoutes()
                                + ", skippedNotDueRoutes=" + routeSelection.skippedNotDueRoutes()
                );
            }
            log.info(
                    "Starting TAGO bus location fetch. cityCode={}, routeCount={}, dueRouteCount={}, skippedInactiveRoutes={}, skippedNotDueRoutes={}, concurrency={}",
                    cityCode,
                    routes.size(),
                    dueRoutes.size(),
                    routeSelection.skippedInactiveRoutes(),
                    routeSelection.skippedNotDueRoutes(),
                    concurrency
            );
            attemptRegistry.markAttempted(cityCode, dueRouteIds, now);
            long fetchStartedAt = System.nanoTime();
            List<ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.RouteReference,
                    List<LoadTagoLocationPort.TagoBusLocation>>> results = ConcurrentCollectionSupport.execute(
                    "bus-location",
                    dueRoutes,
                    concurrency,
                    route -> locationPort.loadBusLocations(cityCode, route.sourceRouteId()),
                    route -> List.of()
            );

            List<LoadTagoLocationPort.TagoBusLocation> locations = new ArrayList<>();
            int failureCount = 0;
            List<String> failedRouteIds = new ArrayList<>();
            for (ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.RouteReference,
                    List<LoadTagoLocationPort.TagoBusLocation>> result : results) {
                if (result.failed()) {
                    failureCount++;
                    failedRouteIds.add(result.source().sourceRouteId());
                    if (failureCount <= FAILURE_MESSAGE_LIMIT) {
                        logRouteFailure(result);
                    } else if (failureCount == FAILURE_MESSAGE_LIMIT + 1) {
                        log.warn("Suppressing further TAGO bus location fetch failure logs.");
                    }
                } else {
                    locations.addAll(result.value());
                }
            }
            log.info(
                    "Finished TAGO bus location fetch. cityCode={}, routeCount={}, dueRouteCount={}, skippedInactiveRoutes={}, skippedNotDueRoutes={}, locationRows={}, failures={}, durationMs={}",
                    cityCode,
                    routes.size(),
                    dueRoutes.size(),
                    routeSelection.skippedInactiveRoutes(),
                    routeSelection.skippedNotDueRoutes(),
                    locations.size(),
                    failureCount,
                    elapsedMillis(fetchStartedAt)
            );

            long saveStartedAt = System.nanoTime();
            int rowCount = saveTransitDataPort.saveLocationSnapshots(cityCode, locations);
            log.info(
                    "Saved TAGO bus location snapshots. cityCode={}, rows={}, durationMs={}",
                    cityCode,
                    rowCount,
                    elapsedMillis(saveStartedAt)
            );

            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    resultMessage(rowCount, failureCount, routeSelection, failedRouteIds));
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private static String resultMessage(
            int rowCount,
            int failureCount,
            RouteLocationCollectionPolicy.Selection routeSelection,
            List<String> failedRouteIds
    ) {
        String message = "locations=" + rowCount
                + ", routeFailures=" + failureCount
                + ", skippedInactiveRoutes=" + routeSelection.skippedInactiveRoutes()
                + ", skippedNotDueRoutes=" + routeSelection.skippedNotDueRoutes();
        if (failedRouteIds.isEmpty()) {
            return message;
        }
        int limit = Math.min(FAILURE_MESSAGE_LIMIT, failedRouteIds.size());
        String suffix = failedRouteIds.size() > limit ? ", ..." : "";
        return message + ", failedRouteIds=" + failedRouteIds.subList(0, limit) + suffix;
    }

    private RouteCollectionCandidate toRouteCollectionCandidate(String cityCode, LoadTransitDataPort.RouteReference route) {
        return new RouteCollectionCandidate(
                route.sourceRouteId(),
                route.weekdayIntervalMinutes(),
                route.saturdayIntervalMinutes(),
                route.sundayIntervalMinutes(),
                route.firstVehicleTime(),
                route.lastVehicleTime(),
                latest(route.lastLocationCollectedAt(), attemptRegistry.lastAttemptedAt(cityCode, route.sourceRouteId()))
        );
    }

    private static Instant latest(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static CollectionStatus status(int rowCount, int failureCount) {
        if (rowCount == 0 && failureCount == 0) {
            return CollectionStatus.EMPTY;
        }
        return failureCount > 0 ? CollectionStatus.PARTIAL : CollectionStatus.SUCCESS;
    }

    private static void logRouteFailure(
            ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.RouteReference,
                    List<LoadTagoLocationPort.TagoBusLocation>> result
    ) {
        log.warn(
                "TAGO bus location fetch failed. sourceRouteId={}, errorType={}, detail={}",
                result.source().sourceRouteId(),
                result.failure().getClass().getSimpleName(),
                CollectionFailureMessages.describe(result.failure())
        );
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
