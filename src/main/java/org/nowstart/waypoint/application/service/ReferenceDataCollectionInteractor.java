package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.waypoint.application.port.in.CollectReferenceDataUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.config.TagoCollectionProperties;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataCollectionInteractor implements CollectReferenceDataUseCase {

    private static final int PROGRESS_LOG_INTERVAL = 50;
    private static final int FAILURE_LOG_LIMIT = 10;

    private final TagoCityCodeResolver cityCodeResolver;
    private final LoadTagoRoutePort routePort;
    private final SaveTransitDataPort saveTransitDataPort;
    private final CollectionRunSupport runSupport;
    private final ReferenceDataCollectionState collectionState;
    private final TagoCollectionProperties collectionProperties;

    @Override
    public CollectionResult collect() {
        CollectionRunSupport.CollectionRun run = runSupport.start(
                CollectionApiType.REFERENCE_DATA,
                "changwon-reference-data",
                "{\"scope\":\"routes-and-route-stops\"}"
        );
        try {
            String cityCode = cityCodeResolver.resolve();
            List<LoadTagoRoutePort.TagoRoute> rawRoutes = routePort.loadRoutes(cityCode);
            List<LoadTagoRoutePort.TagoRoute> routes = distinctBySourceRouteId(rawRoutes);
            log.info(
                    "Loaded TAGO reference routes. cityCode={}, routeCount={}, rawRouteCount={}",
                    cityCode,
                    routes.size(),
                    rawRoutes.size()
            );
            int routeCount = saveTransitDataPort.saveRoutes(cityCode, routes);
            log.info("Saved TAGO base reference routes. cityCode={}, routeCount={}", cityCode, routeCount);
            if (routeCount > 0) {
                collectionState.markRoutesReady();
            }

            EnrichmentResult enrichmentResult = enrichRoutes(cityCode, routes);
            List<LoadTagoRoutePort.TagoRoute> enrichedRoutes = enrichmentResult.routes();
            int routeInfoUpdateCount = saveTransitDataPort.saveRoutes(cityCode, enrichedRoutes);
            log.info("Updated TAGO enriched reference routes. cityCode={}, routeCount={}", cityCode, routeInfoUpdateCount);
            if (routeInfoUpdateCount > 0) {
                collectionState.markRoutesReady();
            }

            RouteStopCollectionResult routeStopCollectionResult = collectRouteStops(cityCode, enrichedRoutes);
            int stopCount = routeStopCollectionResult.stopCount();
            int failureCount = enrichmentResult.failureCount() + routeStopCollectionResult.failureCount();

            int rowCount = routeCount + stopCount;
            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    "routes=" + routeCount
                            + ", routeStops=" + stopCount
                            + ", routeInfoUpdates=" + routeInfoUpdateCount
                            + ", routeInfoFailures=" + enrichmentResult.failureCount()
                            + ", failures=" + failureCount);
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private EnrichmentResult enrichRoutes(
            String cityCode,
            List<LoadTagoRoutePort.TagoRoute> routes
    ) {
        List<RouteTaskResult<LoadTagoRoutePort.TagoRoute>> results = executeRouteTasks(
                "route-info",
                routes,
                route -> routePort.loadRouteInfo(cityCode, route.sourceRouteId()).orElse(route),
                Function.identity()
        );
        List<LoadTagoRoutePort.TagoRoute> enrichedRoutes = new ArrayList<>(results.size());
        int failureCount = 0;
        int processedRouteCount = 0;
        for (RouteTaskResult<LoadTagoRoutePort.TagoRoute> result : results) {
            enrichedRoutes.add(result.value());
            if (result.failed()) {
                failureCount++;
            }
            processedRouteCount++;
            if (shouldLogProgress(processedRouteCount, routes.size())) {
                log.info(
                        "Enriching TAGO reference routes. processedRoutes={}, totalRoutes={}, failures={}",
                        processedRouteCount,
                        routes.size(),
                        failureCount
                );
            }
        }
        return new EnrichmentResult(enrichedRoutes, failureCount);
    }

    private RouteStopCollectionResult collectRouteStops(
            String cityCode,
            List<LoadTagoRoutePort.TagoRoute> routes
    ) {
        List<RouteTaskResult<RouteStops>> results = executeRouteTasks(
                "route-stops",
                routes,
                route -> new RouteStops(route, routePort.loadRouteStops(cityCode, route.sourceRouteId())),
                route -> new RouteStops(route, List.of())
        );
        int stopCount = 0;
        int failureCount = 0;
        int processedRouteCount = 0;
        AtomicInteger saveFailureLogCount = new AtomicInteger();
        for (RouteTaskResult<RouteStops> result : results) {
            RouteStops routeStops = result.value();
            try {
                if (result.failed()) {
                    failureCount++;
                } else {
                    stopCount += saveTransitDataPort.saveRouteStops(
                            cityCode,
                            routeStops.route().sourceRouteId(),
                            routeStops.stops()
                    );
                }
            } catch (RuntimeException ex) {
                failureCount++;
                logRouteTaskFailure("route-stop-save", routeStops.route(), ex, saveFailureLogCount);
            }
            processedRouteCount++;
            if (shouldLogProgress(processedRouteCount, routes.size())) {
                log.info(
                        "Collecting TAGO reference route stops. processedRoutes={}, totalRoutes={}, stopRows={}, failures={}",
                        processedRouteCount,
                        routes.size(),
                        stopCount,
                        failureCount
                );
            }
        }
        return new RouteStopCollectionResult(stopCount, failureCount);
    }

    private <T> List<RouteTaskResult<T>> executeRouteTasks(
            String taskName,
            List<LoadTagoRoutePort.TagoRoute> routes,
            Function<LoadTagoRoutePort.TagoRoute, T> task,
            Function<LoadTagoRoutePort.TagoRoute, T> fallback
    ) {
        int concurrency = collectionProperties.referenceDataConcurrency();
        AtomicInteger failureLogCount = new AtomicInteger();
        List<ConcurrentCollectionSupport.TaskResult<LoadTagoRoutePort.TagoRoute, T>> taskResults =
                ConcurrentCollectionSupport.execute(
                        taskName,
                        routes,
                        concurrency,
                        task,
                        fallback
                );
        List<RouteTaskResult<T>> results = new ArrayList<>(taskResults.size());
        for (ConcurrentCollectionSupport.TaskResult<LoadTagoRoutePort.TagoRoute, T> result : taskResults) {
            if (result.failed()) {
                logRouteTaskFailure(taskName, result.source(), result.failure(), failureLogCount);
            }
            results.add(new RouteTaskResult<>(result.value(), result.failed()));
        }
        return results;
    }

    private static void logRouteTaskFailure(
            String taskName,
            LoadTagoRoutePort.TagoRoute route,
            RuntimeException ex,
            AtomicInteger failureLogCount
    ) {
        int logIndex = failureLogCount.incrementAndGet();
        if (logIndex <= FAILURE_LOG_LIMIT) {
            log.warn(
                    "TAGO reference {} failed. sourceRouteId={}, errorType={}, detail={}",
                    taskName,
                    route.sourceRouteId(),
                    ex.getClass().getSimpleName(),
                    CollectionFailureMessages.describe(ex)
            );
        } else if (logIndex == FAILURE_LOG_LIMIT + 1) {
            log.warn("Suppressing further TAGO reference {} failure logs.", taskName);
        }
    }

    private static boolean shouldLogProgress(int processedCount, int totalCount) {
        return processedCount == totalCount || processedCount % PROGRESS_LOG_INTERVAL == 0;
    }

    private static List<LoadTagoRoutePort.TagoRoute> distinctBySourceRouteId(
            List<LoadTagoRoutePort.TagoRoute> routes
    ) {
        Map<String, LoadTagoRoutePort.TagoRoute> routeMap = new LinkedHashMap<>();
        for (LoadTagoRoutePort.TagoRoute route : routes) {
            if (route.sourceRouteId() != null) {
                routeMap.put(route.sourceRouteId(), route);
            }
        }
        return List.copyOf(routeMap.values());
    }

    private static CollectionStatus status(int rowCount, int failureCount) {
        if (rowCount == 0 && failureCount == 0) {
            return CollectionStatus.EMPTY;
        }
        return failureCount > 0 ? CollectionStatus.PARTIAL : CollectionStatus.SUCCESS;
    }

    private record EnrichmentResult(
            List<LoadTagoRoutePort.TagoRoute> routes,
            int failureCount
    ) {
    }

    private record RouteStops(
            LoadTagoRoutePort.TagoRoute route,
            List<LoadTagoRoutePort.TagoRouteStop> stops
    ) {
    }

    private record RouteStopCollectionResult(
            int stopCount,
            int failureCount
    ) {
    }

    private record RouteTaskResult<T>(
            T value,
            boolean failed
    ) {
    }
}
