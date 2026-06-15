package org.nowstart.waypoint.application.port.out;

import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface SaveTransitDataPort {

    Long startCollectionRun(CollectionApiType apiType, String requestKey, String requestParamsJson);

    void finishCollectionRun(
            Long runId,
            CollectionStatus status,
            int httpStatus,
            String resultCode,
            String resultMessage,
            int rowCount,
            String errorMessage
    );

    int saveRoutes(String cityCode, List<LoadTagoRoutePort.TagoRoute> routes);

    int saveRouteStops(String cityCode, String sourceRouteId, List<LoadTagoRoutePort.TagoRouteStop> stops);

    int saveLocationSnapshots(String cityCode, List<LoadTagoLocationPort.TagoBusLocation> locations);

    int saveArrivalSnapshots(
            String cityCode,
            List<LoadTagoArrivalPort.TagoBusArrival> arrivals,
            Map<String, Instant> collectedAtByStop
    );
}
