package org.nowstart.waypoint.adapter.out.tago;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.application.port.out.TagoApiException;
import org.nowstart.waypoint.config.TagoProperties;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.assertj.core.api.ThrowableAssert.ThrowingCallable;

class TagoClientTest {

    @Test
    @DisplayName("TAGO_SERVICE_KEY가 공백이면 서비스 키 누락 예외를 던진다")
    void fetchItemsWithBlankServiceKeyThrowsMissingServiceKey() {
        // given: 공백 서비스 키로 생성한 TAGO 클라이언트
        TagoProperties properties = new TagoProperties(
                "http://localhost:1",
                "   ",
                "창원시",
                "38010",
                100,
                1,
                Duration.ofMillis(10),
                Duration.ofMillis(10)
        );
        TagoClient client = new TagoClient(properties, new TagoResponseParser(new ObjectMapper()));

        // when: fetchItems를 호출한다
        ThrowingCallable fetchItems = () -> client.fetchItems("BusRouteInfoInqireService", "getCtyCodeList", Map.of());

        // then: API 호출 전에 서비스 키 누락 예외를 던진다
        thenThrownBy(fetchItems)
                .isInstanceOfSatisfying(TagoApiException.class, exception ->
                        then(exception.getResultCode()).isEqualTo("MISSING_SERVICE_KEY"));
    }
}
