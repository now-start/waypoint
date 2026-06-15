package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.config.TagoCollectionProperties;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Override
    public CollectionResult collect() {
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
            LocalTime now = LocalTime.now(TAGO_ZONE);
            List<LoadTransitDataPort.RouteReference> activeRoutes = routes.stream()
                    .filter(route -> RouteOperationWindow.isActive(route, now))
                    .toList();
            int skippedRouteCount = routes.size() - activeRoutes.size();
            if (activeRoutes.isEmpty()) {
                return runSupport.finish(
                        run,
                        CollectionStatus.EMPTY,
                        0,
                        0,
                        "운행 시간대에 해당하는 노선이 없습니다. skippedInactiveRoutes=" + skippedRouteCount
                );
            }
            log.info(
                    "Starting TAGO bus location fetch. cityCode={}, routeCount={}, activeRouteCount={}, skippedInactiveRoutes={}, concurrency={}",
                    cityCode,
                    routes.size(),
                    activeRoutes.size(),
                    skippedRouteCount,
                    concurrency
            );
            long fetchStartedAt = System.nanoTime();
            List<ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.RouteReference,
                    List<LoadTagoLocationPort.TagoBusLocation>>> results = ConcurrentCollectionSupport.execute(
                    "bus-location",
                    activeRoutes,
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
                    "Finished TAGO bus location fetch. cityCode={}, routeCount={}, activeRouteCount={}, skippedInactiveRoutes={}, locationRows={}, failures={}, durationMs={}",
                    cityCode,
                    routes.size(),
                    activeRoutes.size(),
                    skippedRouteCount,
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
                    resultMessage(rowCount, failureCount, skippedRouteCount, failedRouteIds));
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private static String resultMessage(
            int rowCount,
            int failureCount,
            int skippedRouteCount,
            List<String> failedRouteIds
    ) {
        String message = "locations=" + rowCount
                + ", routeFailures=" + failureCount
                + ", skippedInactiveRoutes=" + skippedRouteCount;
        if (failedRouteIds.isEmpty()) {
            return message;
        }
        int limit = Math.min(FAILURE_MESSAGE_LIMIT, failedRouteIds.size());
        String suffix = failedRouteIds.size() > limit ? ", ..." : "";
        return message + ", failedRouteIds=" + failedRouteIds.subList(0, limit) + suffix;
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
