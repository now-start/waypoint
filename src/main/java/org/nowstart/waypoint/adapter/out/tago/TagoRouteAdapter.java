package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.out.LoadTagoRoutePort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TagoRouteAdapter implements LoadTagoRoutePort {

    private static final String SERVICE = "BusRouteInfoInqireService";

    private final TagoClient client;

    @Override
    public List<TagoCity> loadCities() {
        return client.fetchItems(SERVICE, "getCtyCodeList", Map.of()).stream()
                .map(item -> new TagoCity(
                        TagoResponseParser.text(item, "citycode", "cityCode"),
                        TagoResponseParser.text(item, "cityname", "cityName")
                ))
                .filter(city -> city.cityCode() != null && city.cityName() != null)
                .toList();
    }

    @Override
    public List<TagoRoute> loadRoutes(String cityCode) {
        return client.fetchItems(SERVICE, "getRouteNoList", Map.of("cityCode", cityCode)).stream()
                .map(this::toRoute)
                .filter(route -> route.sourceRouteId() != null)
                .toList();
    }

    @Override
    public Optional<TagoRoute> loadRouteInfo(String cityCode, String sourceRouteId) {
        return client.fetchItems(SERVICE, "getRouteInfoIem", Map.of(
                        "cityCode", cityCode,
                        "routeId", sourceRouteId
                )).stream()
                .findFirst()
                .map(this::toRoute)
                .map(route -> route.sourceRouteId() == null
                        ? new TagoRoute(
                        sourceRouteId,
                        route.routeNo(),
                        route.routeType(),
                        route.startNodeName(),
                        route.endNodeName(),
                        route.weekdayIntervalMinutes(),
                        route.saturdayIntervalMinutes(),
                        route.sundayIntervalMinutes(),
                        route.firstVehicleTime(),
                        route.lastVehicleTime()
                )
                        : route);
    }

    @Override
    public List<TagoRouteStop> loadRouteStops(String cityCode, String sourceRouteId) {
        return client.fetchItems(SERVICE, "getRouteAcctoThrghSttnList", Map.of(
                        "cityCode", cityCode,
                        "routeId", sourceRouteId
                )).stream()
                .map(this::toRouteStop)
                .filter(stop -> stop.sourceNodeId() != null)
                .toList();
    }

    private TagoRoute toRoute(JsonNode item) {
        return new TagoRoute(
                TagoResponseParser.text(item, "routeid", "routeId"),
                TagoResponseParser.text(item, "routeno", "routeNo"),
                TagoResponseParser.text(item, "routetp", "routeTp", "routeType"),
                TagoResponseParser.text(item, "startnodenm", "startNodeNm", "startNodeName"),
                TagoResponseParser.text(item, "endnodenm", "endNodeNm", "endNodeName"),
                TagoResponseParser.integer(item, "intervaltime", "intervalTime"),
                TagoResponseParser.integer(item, "intervalsat", "intervalSat", "intervalsattime", "intervalSatTime"),
                TagoResponseParser.integer(item, "intervalsun", "intervalSun", "intervalsuntime", "intervalSunTime"),
                TagoResponseParser.text(item, "startvehicletime", "startVehicleTime"),
                TagoResponseParser.text(item, "endvehicletime", "endVehicleTime")
        );
    }

    private TagoRouteStop toRouteStop(JsonNode item) {
        return new TagoRouteStop(
                TagoResponseParser.text(item, "nodeid", "nodeId"),
                TagoResponseParser.text(item, "nodeno", "nodeNo"),
                TagoResponseParser.text(item, "nodenm", "nodeNm", "nodeName"),
                TagoResponseParser.integer(item, "nodeord", "nodeOrd", "nodeOrder"),
                TagoResponseParser.decimal(item, "gpslati", "gpsLati", "gpsLatitude"),
                TagoResponseParser.decimal(item, "gpslong", "gpsLong", "gpsLongitude")
        );
    }
}
