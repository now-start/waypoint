package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.JsonNode;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class TagoArrivalAdapter implements LoadTagoArrivalPort {

    private static final String SERVICE = "ArvlInfoInqireService";

    private final TagoClient client;

    public TagoArrivalAdapter(TagoClient client) {
        this.client = client;
    }

    @Override
    public List<TagoBusArrival> loadArrivals(String cityCode, String sourceNodeId) {
        Instant collectedAt = Instant.now();
        return client.fetchItems(SERVICE, "getSttnAcctoArvlPrearngeInfoList", Map.of(
                        "cityCode", cityCode,
                        "nodeId", sourceNodeId
                )).stream()
                .map(item -> toArrival(item, collectedAt, sourceNodeId))
                .toList();
    }

    private TagoBusArrival toArrival(JsonNode item, Instant collectedAt, String fallbackNodeId) {
        return new TagoBusArrival(
                firstNonNull(TagoResponseParser.text(item, "nodeid", "nodeId"), fallbackNodeId),
                TagoResponseParser.text(item, "nodenm", "nodeNm", "nodeName"),
                TagoResponseParser.text(item, "routeid", "routeId"),
                TagoResponseParser.text(item, "routeno", "routeNo"),
                TagoResponseParser.text(item, "routetp", "routeTp", "routeType"),
                TagoResponseParser.integer(item, "arrprevstationcnt", "arrPrevStationCnt"),
                TagoResponseParser.arrivalRemainingMinutes(item),
                TagoResponseParser.arrivalExpectedAt(item, collectedAt),
                TagoResponseParser.text(item, "vehicletp", "vehicleTp", "vehicleType"),
                collectedAt
        );
    }

    private static String firstNonNull(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
