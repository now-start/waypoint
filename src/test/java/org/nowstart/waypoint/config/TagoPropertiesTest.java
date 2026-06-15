package org.nowstart.waypoint.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.then;

class TagoPropertiesTest {

    @Test
    @DisplayName("서비스 키에 바깥 따옴표가 포함되면 제거한다")
    void serviceKeyStripsSurroundingQuotes() {
        // when: 실행구성 환경변수에 따옴표가 포함된 형태로 서비스 키가 전달된다
        TagoProperties properties = new TagoProperties(
                "http://apis.data.go.kr/1613000",
                "\"decoded-key\"",
                "창원시",
                "38010",
                1000,
                Duration.ofSeconds(5),
                Duration.ofSeconds(20)
        );

        // then: 실제 호출에는 따옴표를 제외한 키를 사용한다
        then(properties.serviceKey()).isEqualTo("decoded-key");
    }
}
