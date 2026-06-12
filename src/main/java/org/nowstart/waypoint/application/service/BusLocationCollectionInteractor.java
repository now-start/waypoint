package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusLocationCollectionInteractor implements CollectBusLocationUseCase {

    private final TagoCityCodeResolver cityCodeResolver;
    private final LoadTransitDataPort loadTransitDataPort;
    private final LoadTagoLocationPort locationPort;
    private final SaveTransitDataPort saveTransitDataPort;
    private final CollectionRunSupport runSupport;

    public BusLocationCollectionInteractor(
            TagoCityCodeResolver cityCodeResolver,
            LoadTransitDataPort loadTransitDataPort,
            LoadTagoLocationPort locationPort,
            SaveTransitDataPort saveTransitDataPort,
            CollectionRunSupport runSupport
    ) {
        this.cityCodeResolver = cityCodeResolver;
        this.loadTransitDataPort = loadTransitDataPort;
        this.locationPort = locationPort;
        this.saveTransitDataPort = saveTransitDataPort;
        this.runSupport = runSupport;
    }

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

            int rowCount = 0;
            int failureCount = 0;
            for (LoadTransitDataPort.RouteReference route : routes) {
                try {
                    List<LoadTagoLocationPort.TagoBusLocation> locations =
                            locationPort.loadBusLocations(cityCode, route.sourceRouteId());
                    rowCount += saveTransitDataPort.saveLocationSnapshots(cityCode, route.sourceRouteId(), locations);
                } catch (RuntimeException ex) {
                    failureCount++;
                }
            }

            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    "locations=" + rowCount + ", routeFailures=" + failureCount);
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private static CollectionStatus status(int rowCount, int failureCount) {
        if (rowCount == 0 && failureCount == 0) {
            return CollectionStatus.EMPTY;
        }
        return failureCount > 0 ? CollectionStatus.PARTIAL : CollectionStatus.SUCCESS;
    }
}
