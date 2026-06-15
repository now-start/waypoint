package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.out.LoadTagoLocationPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TagoLocationAdapter implements LoadTagoLocationPort {

    private static final String SERVICE = "BusLcInfoInqireService";

    private final TagoClient client;

    @Override
    public List<TagoBusLocation> loadBusLocations(String cityCode, String sourceRouteId) {
        Instant collectedAt = Instant.now();
        return client.fetchItems(SERVICE, "getRouteAcctoBusLcList", Map.of(
                        "cityCode", cityCode,
                        "routeId", sourceRouteId
                )).stream()
                .map(item -> toLocation(item, collectedAt, sourceRouteId))
                .toList();
    }

    private TagoBusLocation toLocation(JsonNode item, Instant collectedAt, String fallbackRouteId) {
        return new TagoBusLocation(
                firstNonNull(TagoResponseParser.text(item, "routeid", "routeId"), fallbackRouteId),
                TagoResponseParser.text(item, "routeno", "routeNo", "routenm", "routeNm"),
                TagoResponseParser.text(item, "vehicleno", "vehicleNo"),
                TagoResponseParser.text(item, "nodeid", "nodeId"),
                TagoResponseParser.integer(item, "nodeord", "nodeOrd", "nodeOrder"),
                TagoResponseParser.decimal(item, "gpslati", "gpsLati", "gpsLatitude"),
                TagoResponseParser.decimal(item, "gpslong", "gpsLong", "gpsLongitude"),
                collectedAt
        );
    }

    private static String firstNonNull(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
