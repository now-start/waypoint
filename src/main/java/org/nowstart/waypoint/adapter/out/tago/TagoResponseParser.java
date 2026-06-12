package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nowstart.waypoint.application.port.out.TagoApiException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class TagoResponseParser {

    private final ObjectMapper objectMapper;

    public TagoResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedTagoResponse parse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode response = root.path("response");
            JsonNode header = response.path("header");
            JsonNode body = response.path("body");
            JsonNode itemNode = body.path("items").path("item");

            List<JsonNode> items = new ArrayList<>();
            if (itemNode.isArray()) {
                itemNode.forEach(items::add);
            } else if (itemNode.isObject()) {
                items.add(itemNode);
            }

            return new ParsedTagoResponse(
                    text(header, "resultCode"),
                    text(header, "resultMsg", "resultMessage"),
                    integer(body, "totalCount"),
                    items,
                    rawBody
            );
        } catch (IOException ex) {
            throw new TagoApiException("TAGO 응답 파싱에 실패했습니다.", 200, "PARSE_ERROR", ex.getMessage(), ex);
        }
    }

    public static String text(JsonNode node, String... names) {
        JsonNode value = find(node, names);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    public static Integer integer(JsonNode node, String... names) {
        String text = text(node, names);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Double decimal(JsonNode node, String... names) {
        String text = text(node, names);
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Instant arrivalExpectedAt(JsonNode node, Instant collectedAt) {
        Integer remainingSeconds = integer(node, "arrtime", "arrTime", "arrivalTime", "predictTime");
        if (remainingSeconds == null || remainingSeconds < 0) {
            return null;
        }
        return collectedAt.plusSeconds(remainingSeconds);
    }

    public static Integer arrivalRemainingMinutes(JsonNode node) {
        Integer remainingSeconds = integer(node, "arrtime", "arrTime", "arrivalTime", "predictTime");
        if (remainingSeconds == null || remainingSeconds < 0) {
            return null;
        }
        return (int) Math.ceil(remainingSeconds / 60.0);
    }

    private static JsonNode find(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode direct = node.get(name);
            if (direct != null) {
                return direct;
            }
            JsonNode lower = node.get(name.toLowerCase(Locale.ROOT));
            if (lower != null) {
                return lower;
            }
        }
        return null;
    }

    public record ParsedTagoResponse(
            String resultCode,
            String resultMessage,
            Integer totalCount,
            List<JsonNode> items,
            String rawBody
    ) {

        public boolean isSuccess() {
            return "00".equals(resultCode) || "0".equals(resultCode);
        }
    }
}
