package org.nowstart.waypoint.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class TagoCollectionPropertiesTest {

    @Test
    @DisplayName("수집 동시성을 지정하지 않으면 4를 기본값으로 사용한다")
    void collectionConcurrencyDefaultsToFour() {
        // when: 수집 동시성을 지정하지 않는다
        TagoCollectionProperties properties = new TagoCollectionProperties(0, 0, 0, 0, 0);

        // then: 기본 동시성 4를 사용한다
        then(properties.referenceDataConcurrency()).isEqualTo(4);
        then(properties.locationConcurrency()).isEqualTo(4);
        then(properties.arrivalConcurrency()).isEqualTo(4);
        then(properties.rateLimit()).isEqualTo(25);
        then(properties.arrivalMaxStopsPerRun()).isEqualTo(30);
    }

    @Test
    @DisplayName("수집 동시성과 TPS 제한, 도착정보 정류소 제한은 TAGO 상한 아래로 제한한다")
    void collectionLimitsAreCapped() {
        // when: 과도한 동시성, TPS, 정류소 수를 지정한다
        TagoCollectionProperties properties = new TagoCollectionProperties(20, 20, 20, 100, 1_000);

        // then: TAGO API 과호출을 막기 위해 안전한 상한으로 제한한다
        then(properties.referenceDataConcurrency()).isEqualTo(8);
        then(properties.locationConcurrency()).isEqualTo(8);
        then(properties.arrivalConcurrency()).isEqualTo(8);
        then(properties.rateLimit()).isEqualTo(30);
        then(properties.arrivalMaxStopsPerRun()).isEqualTo(500);
    }
}
