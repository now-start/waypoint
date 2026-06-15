package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.application.port.out.TagoApiException;
import org.nowstart.waypoint.config.TagoProperties;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TagoClientTest {

    @Test
    @DisplayName("TAGO_SERVICE_KEY가 공백이면 서비스 키 누락 예외를 던진다")
    void fetchItemsWithBlankServiceKeyThrowsMissingServiceKey() {
        // given: 공백 서비스 키로 생성한 TAGO 클라이언트
        TagoProperties properties = new TagoProperties(
                "http://localhost:1",
                "   ",
                "38010",
                100,
                Duration.ofMillis(10),
                Duration.ofMillis(10)
        );
        TagoRequestRateLimiter rateLimiter = mock(TagoRequestRateLimiter.class);
        TagoClient client = new TagoClient(properties, new TagoResponseParser(new ObjectMapper()), rateLimiter);

        // when: fetchItems를 호출한다
        ThrowingCallable fetchItems = () -> client.fetchItems("BusRouteInfoInqireService", "getCtyCodeList", Map.of());

        // then: API 호출 전에 서비스 키 누락 예외를 던진다
        thenThrownBy(fetchItems)
                .isInstanceOfSatisfying(TagoApiException.class, exception ->
                        then(exception.getResultCode()).isEqualTo("MISSING_SERVICE_KEY"));
        verify(rateLimiter, never()).acquire();
    }

    @Test
    @DisplayName("TAGO 전체 건수를 채울 때까지 다음 페이지를 계속 조회한다")
    void fetchItemsReadsPagesUntilTotalCount() throws IOException {
        // given: 3페이지에 걸쳐 5건을 반환하는 TAGO API
        AtomicInteger callCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/BusRouteInfoInqireService/getRouteList", exchange -> {
            callCount.incrementAndGet();
            int pageNo = queryParam(exchange.getRequestURI().getRawQuery(), "pageNo");
            String items = switch (pageNo) {
                case 1 -> "{\"id\":\"1\"},{\"id\":\"2\"}";
                case 2 -> "{\"id\":\"3\"},{\"id\":\"4\"}";
                default -> "{\"id\":\"5\"}";
            };
            byte[] body = tagoResponse(5, items).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            TagoProperties properties = new TagoProperties(
                    "http://localhost:" + server.getAddress().getPort(),
                    "decoded-service-key",
                    "38010",
                    2,
                    Duration.ofMillis(500),
                    Duration.ofMillis(500)
            );
            TagoRequestRateLimiter rateLimiter = mock(TagoRequestRateLimiter.class);
            TagoClient client = new TagoClient(properties, new TagoResponseParser(new ObjectMapper()), rateLimiter);

            // when: 전체 항목을 조회한다
            List<?> items = client.fetchItems("BusRouteInfoInqireService", "getRouteList", Map.of());

            // then: totalCount를 채울 때까지 모든 페이지를 조회한다
            then(items).hasSize(5);
            then(callCount).hasValue(3);
            verify(rateLimiter, times(3)).acquire();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("디코딩 서비스 키는 실제 HTTP 호출에서 한 번만 URL 인코딩한다")
    void fetchItemsEncodesDecodedServiceKeyOnce() throws IOException {
        // given: URL 인코딩이 필요한 문자를 포함한 디코딩 서비스 키
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/BusRouteInfoInqireService/getCtyCodeList", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = tagoResponse(0, "").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            TagoProperties properties = new TagoProperties(
                    "http://localhost:" + server.getAddress().getPort(),
                    "abc+def==",
                    "38010",
                    100,
                    Duration.ofMillis(500),
                    Duration.ofMillis(500)
            );
            TagoRequestRateLimiter rateLimiter = mock(TagoRequestRateLimiter.class);
            TagoClient client = new TagoClient(properties, new TagoResponseParser(new ObjectMapper()), rateLimiter);

            // when: TAGO API를 호출한다
            client.fetchItems("BusRouteInfoInqireService", "getCtyCodeList", Map.of());

            // then: +와 =가 한 번만 인코딩되고 % 문자가 다시 인코딩되지 않는다
            then(rawQuery.get())
                    .contains("serviceKey=abc%2Bdef%3D%3D")
                    .doesNotContain("%252B")
                    .doesNotContain("%253D");
            verify(rateLimiter).acquire();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("totalCount가 없는 응답에서 페이지가 가득 차면 전체 여부를 판단할 수 없어 실패한다")
    void fetchItemsWithFullPageWithoutTotalCountThrows() throws IOException {
        // given: totalCount 없이 full page를 반환하는 TAGO API
        AtomicInteger callCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/BusRouteInfoInqireService/getRouteList", exchange -> {
            callCount.incrementAndGet();
            byte[] body = tagoResponseWithoutTotalCount("{\"id\":\"1\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            TagoProperties properties = new TagoProperties(
                    "http://localhost:" + server.getAddress().getPort(),
                    "decoded-service-key",
                    "38010",
                    1,
                    Duration.ofMillis(500),
                    Duration.ofMillis(500)
            );
            TagoRequestRateLimiter rateLimiter = mock(TagoRequestRateLimiter.class);
            TagoClient client = new TagoClient(properties, new TagoResponseParser(new ObjectMapper()), rateLimiter);

            // when: 전체 항목을 조회한다
            ThrowingCallable fetchItems =
                    () -> client.fetchItems("BusRouteInfoInqireService", "getRouteList", Map.of());

            // then: 무한 페이징 대신 totalCount 누락 예외를 던진다
            thenThrownBy(fetchItems)
                    .isInstanceOfSatisfying(TagoApiException.class, exception ->
                            then(exception.getResultCode()).isEqualTo("MISSING_TOTAL_COUNT"));
            then(callCount).hasValue(1);
            verify(rateLimiter).acquire();
        } finally {
            server.stop(0);
        }
    }

    private static int queryParam(String query, String name) {
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return Integer.parseInt(pair[1]);
            }
        }
        throw new IllegalArgumentException("query parameter not found: " + name);
    }

    private static String tagoResponse(int totalCount, String items) {
        return """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "OK"
                    },
                    "body": {
                      "totalCount": "%d",
                      "items": {
                        "item": [%s]
                      }
                    }
                  }
                }
                """.formatted(totalCount, items);
    }

    private static String tagoResponseWithoutTotalCount(String items) {
        return """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "OK"
                    },
                    "body": {
                      "items": {
                        "item": [%s]
                      }
                    }
                  }
                }
                """.formatted(items);
    }
}
