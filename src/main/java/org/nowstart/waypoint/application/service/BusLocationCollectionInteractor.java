package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
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
public class BusLocationCollectionInteractor implements CollectBusLocationUseCase {

    private static final int FAILURE_MESSAGE_LIMIT = 10;

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

            List<ConcurrentCollectionSupport.TaskResult<
                    LoadTransitDataPort.RouteReference,
                    List<LoadTagoLocationPort.TagoBusLocation>>> results = ConcurrentCollectionSupport.execute(
                    routes,
                    collectionProperties.locationConcurrency(),
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
                } else {
                    locations.addAll(result.value());
                }
            }
            int rowCount = saveTransitDataPort.saveLocationSnapshots(cityCode, locations);

            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    resultMessage(rowCount, failureCount, failedRouteIds));
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private static String resultMessage(int rowCount, int failureCount, List<String> failedRouteIds) {
        String message = "locations=" + rowCount + ", routeFailures=" + failureCount;
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
}
