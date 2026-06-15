package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.in.CollectReferenceDataUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReferenceDataCollectionInteractor implements CollectReferenceDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataCollectionInteractor.class);
    private static final int PROGRESS_LOG_INTERVAL = 50;

    private final TagoCityCodeResolver cityCodeResolver;
    private final LoadTagoRoutePort routePort;
    private final SaveTransitDataPort saveTransitDataPort;
    private final CollectionRunSupport runSupport;

    public ReferenceDataCollectionInteractor(
            TagoCityCodeResolver cityCodeResolver,
            LoadTagoRoutePort routePort,
            SaveTransitDataPort saveTransitDataPort,
            CollectionRunSupport runSupport
    ) {
        this.cityCodeResolver = cityCodeResolver;
        this.routePort = routePort;
        this.saveTransitDataPort = saveTransitDataPort;
        this.runSupport = runSupport;
    }

    @Override
    public CollectionResult collect() {
        CollectionRunSupport.CollectionRun run = runSupport.start(
                CollectionApiType.REFERENCE_DATA,
                "changwon-reference-data",
                "{\"scope\":\"routes-and-route-stops\"}"
        );
        try {
            String cityCode = cityCodeResolver.resolve();
            List<LoadTagoRoutePort.TagoRoute> routes = routePort.loadRoutes(cityCode);
            log.info("Loaded TAGO reference routes. cityCode={}, routeCount={}", cityCode, routes.size());
            EnrichmentResult enrichmentResult = enrichRoutes(cityCode, routes);
            List<LoadTagoRoutePort.TagoRoute> enrichedRoutes = enrichmentResult.routes();
            int routeCount = saveTransitDataPort.saveRoutes(cityCode, enrichedRoutes);
            log.info("Saved TAGO reference routes. cityCode={}, routeCount={}", cityCode, routeCount);

            int stopCount = 0;
            int failureCount = enrichmentResult.failureCount();
            int processedRouteCount = 0;
            for (LoadTagoRoutePort.TagoRoute route : enrichedRoutes) {
                try {
                    List<LoadTagoRoutePort.TagoRouteStop> stops = routePort.loadRouteStops(cityCode, route.sourceRouteId());
                    stopCount += saveTransitDataPort.saveRouteStops(cityCode, route.sourceRouteId(), stops);
                } catch (RuntimeException ex) {
                    failureCount++;
                }
                processedRouteCount++;
                if (shouldLogProgress(processedRouteCount, enrichedRoutes.size())) {
                    log.info(
                            "Collecting TAGO reference route stops. processedRoutes={}, totalRoutes={}, stopRows={}, failures={}",
                            processedRouteCount,
                            enrichedRoutes.size(),
                            stopCount,
                            failureCount
                    );
                }
            }

            int rowCount = routeCount + stopCount;
            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    "routes=" + routeCount
                            + ", routeStops=" + stopCount
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
        List<LoadTagoRoutePort.TagoRoute> enrichedRoutes = new ArrayList<>(routes.size());
        int failureCount = 0;
        int processedRouteCount = 0;
        for (LoadTagoRoutePort.TagoRoute route : routes) {
            try {
                enrichedRoutes.add(routePort.loadRouteInfo(cityCode, route.sourceRouteId()).orElse(route));
            } catch (RuntimeException ignored) {
                failureCount++;
                enrichedRoutes.add(route);
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

    private static boolean shouldLogProgress(int processedCount, int totalCount) {
        return processedCount == totalCount || processedCount % PROGRESS_LOG_INTERVAL == 0;
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
}
