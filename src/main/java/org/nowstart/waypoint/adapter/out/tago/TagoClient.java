package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.JsonNode;
import org.nowstart.waypoint.application.port.out.TagoApiException;
import org.nowstart.waypoint.config.TagoProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TagoClient {

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
    }

    public List<JsonNode> fetchItems(String serviceName, String operationName, Map<String, String> params) {
        requireServiceKey();

        List<JsonNode> allItems = new ArrayList<>();
        int pageNo = 1;
        Integer totalCount = null;

        while (pageNo <= properties.maxPages()) {
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
            totalCount = page.totalCount();
            if (page.items().isEmpty() || totalCount == null || allItems.size() >= totalCount) {
                break;
            }
            pageNo++;
        }

        return allItems;
    }

    private TagoResponseParser.ParsedTagoResponse fetchPage(
            String serviceName,
            String operationName,
            Map<String, String> params
    ) {
        String url = buildUrl(serviceName, operationName, params);
        try {
            String rawBody = restClient.get()
                    .uri(url)
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
            throw new TagoApiException("TAGO API 호출에 실패했습니다.", 0, "CLIENT_ERROR", ex.getMessage(), ex);
        }
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
}
