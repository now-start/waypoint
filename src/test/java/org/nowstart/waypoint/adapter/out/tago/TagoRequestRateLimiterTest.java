package org.nowstart.waypoint.adapter.out.tago;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class TagoRequestRateLimiterTest {

    @Test
    @DisplayName("30 TPS 제한은 초당 30회를 넘지 않도록 나노초 간격을 올림 계산한다")
    void rateLimitThirtyUsesCeilingPermitInterval() {
        then(TagoRequestRateLimiter.permitIntervalNanos(30)).isEqualTo(33_333_334L);
    }
}
