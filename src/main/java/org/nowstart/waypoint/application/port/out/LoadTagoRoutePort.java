package org.nowstart.waypoint.application.port.out;

import java.util.List;
import java.util.Optional;

public interface LoadTagoRoutePort {

    List<TagoCity> loadCities();

    List<TagoRoute> loadRoutes(String cityCode);

    Optional<TagoRoute> loadRouteInfo(String cityCode, String sourceRouteId);

    List<TagoRouteStop> loadRouteStops(String cityCode, String sourceRouteId);

    record TagoCity(
            String cityCode,
            String cityName
    ) {
    }

    record TagoRoute(
            String sourceRouteId,
            String routeNo,
            String routeType,
            String startNodeName,
            String endNodeName,
            Integer weekdayIntervalMinutes,
            Integer saturdayIntervalMinutes,
            Integer sundayIntervalMinutes,
            String firstVehicleTime,
            String lastVehicleTime
    ) {
    }

    record TagoRouteStop(
            String sourceNodeId,
            String nodeNo,
            String nodeName,
            Integer nodeOrder,
            Double gpsLatitude,
            Double gpsLongitude
    ) {
    }
}
