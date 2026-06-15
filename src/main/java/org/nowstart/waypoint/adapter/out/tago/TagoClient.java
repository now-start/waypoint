package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.JsonNode;
import org.nowstart.waypoint.application.port.out.TagoApiException;
import org.nowstart.waypoint.config.TagoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TagoClient {

    private static final Logger log = LoggerFactory.getLogger(TagoClient.class);
    private static final String TYPE_JSON = "json";

    private final TagoProperties properties;
    private final TagoResponseParser parser;
    private final RestClient restClient;

    public TagoClient(TagoProperties properties, TagoResponseParser parser) {
        this.properties = properties;
        this.parser = parser;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        logServiceKeyDiagnostics(properties.serviceKey());
    }

    public List<JsonNode> fetchItems(String serviceName, String operationName, Map<String, String> params) {
        requireServiceKey();

        List<JsonNode> allItems = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            Map<String, String> pageParams = new LinkedHashMap<>(params);
            pageParams.put("pageNo", String.valueOf(pageNo));
            pageParams.put("numOfRows", String.valueOf(properties.numOfRows()));

            TagoResponseParser.ParsedTagoResponse page = fetchPage(serviceName, operationName, pageParams);
            if (!page.isSuccess()) {
                String resultCode = page.resultCode() == null ? "MISSING_RESULT_CODE" : page.resultCode();
                String resultMessage = page.resultMessage() == null ? "TAGO resultCode is missing." : page.resultMessage();
                throw new TagoApiException(
                        "TAGO API 결과코드가 실패입니다.",
                        200,
                        resultCode,
                        resultMessage
                );
            }

            allItems.addAll(page.items());
            if (isLastPage(page, allItems.size())) {
                break;
            }
            pageNo++;
        }

        return allItems;
    }

    private boolean isLastPage(TagoResponseParser.ParsedTagoResponse page, int accumulatedItemCount) {
        if (page.items().isEmpty()) {
            return true;
        }
        Integer totalCount = page.totalCount();
        if (totalCount != null) {
            return accumulatedItemCount >= totalCount;
        }
        if (page.items().size() < properties.numOfRows()) {
            return true;
        }
        throw new TagoApiException(
                "TAGO 응답 totalCount가 없어 전체 페이지 수를 판단할 수 없습니다.",
                200,
                "MISSING_TOTAL_COUNT",
                "totalCount is required when a TAGO page is full."
        );
    }

    private TagoResponseParser.ParsedTagoResponse fetchPage(
            String serviceName,
            String operationName,
            Map<String, String> params
    ) {
        URI uri = buildUri(serviceName, operationName, params);
        try {
            String rawBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            return parser.parse(rawBody == null ? "{}" : rawBody);
        } catch (RestClientResponseException ex) {
            throw new TagoApiException(
                    "TAGO API HTTP 호출에 실패했습니다.",
                    ex.getStatusCode().value(),
                    "HTTP_ERROR",
                    ex.getResponseBodyAsString(),
                    ex
            );
        } catch (TagoApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TagoApiException(
                    "TAGO API 호출에 실패했습니다.",
                    0,
                    "CLIENT_ERROR",
                    ex.getClass().getSimpleName(),
                    ex
            );
        }
    }

    URI buildUri(String serviceName, String operationName, Map<String, String> params) {
        return URI.create(buildUrl(serviceName, operationName, params));
    }

    private String buildUrl(String serviceName, String operationName, Map<String, String> params) {
        StringBuilder builder = new StringBuilder()
                .append(properties.baseUrl())
                .append('/')
                .append(serviceName)
                .append('/')
                .append(operationName)
                .append("?serviceKey=")
                .append(encodeServiceKey(properties.serviceKey()))
                .append("&_type=")
                .append(TYPE_JSON);

        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                builder.append('&')
                        .append(encode(key))
                        .append('=')
                        .append(encode(value));
            }
        });
        return builder.toString();
    }

    private void requireServiceKey() {
        if (!properties.hasServiceKey()) {
            throw new TagoApiException(
                    "TAGO_SERVICE_KEY 환경변수가 비어 있어 TAGO API를 호출할 수 없습니다.",
                    0,
                    "MISSING_SERVICE_KEY",
                    "TAGO service key is blank"
            );
        }
    }

    private static String encodeServiceKey(String serviceKey) {
        if (serviceKey.contains("%")) {
            return serviceKey;
        }
        return encode(serviceKey);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void logServiceKeyDiagnostics(String serviceKey) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("TAGO service key is not configured.");
            return;
        }
        log.info(
                "TAGO service key is configured. length={}, encoded={}, fingerprint={}",
                serviceKey.length(),
                serviceKey.contains("%"),
                fingerprint(serviceKey)
        );
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }
}
