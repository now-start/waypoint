package org.nowstart.waypoint.domain.transit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransitAnomalyPolicyTest {

    private static final TransitAnomalyPolicy.Rule RULE = new TransitAnomalyPolicy.Rule(
            Duration.ofMinutes(30),
            Duration.ofMinutes(3),
            Duration.ofMinutes(5)
    );

    @Test
    @DisplayName("위치 갱신 지연은 요일별 배차로 계산한 수집 주기와 지연 허용값을 기준으로 판정한다")
    void detectsStaleLocationByDynamicCollectionInterval() {
        Instant now = Instant.parse("2026-06-15T12:00:00Z");

        List<TransitAnomalyPolicy.TransitAnomaly> anomalies = TransitAnomalyPolicy.detect(
                List.of(route("CWB101", "101", 12, 30, 40, now.minus(Duration.ofMinutes(16)))),
                List.of(location("CWB101", "101", "BUS-1", now.minus(Duration.ofMinutes(16)))),
                List.of(),
                RULE,
                now,
                DayOfWeek.MONDAY
        );

        assertThat(anomalies).singleElement()
                .satisfies(anomaly -> {
                    assertThat(anomaly.type()).isEqualTo("위치 갱신 지연");
                    assertThat(anomaly.baseline()).isEqualTo("예상 위치 갱신 8분 주기");
                    assertThat(anomaly.observed()).isEqualTo("최근 위치 16분 전");
                });
    }

    @Test
    @DisplayName("도착 스냅샷 간격은 주중 토 일요일 배차 중 해당 요일 기준값과 비교한다")
    void detectsHeadwayAnomalyByDaySpecificHeadway() {
        Instant now = Instant.parse("2026-06-13T12:00:00Z");

        List<TransitAnomalyPolicy.TransitAnomaly> anomalies = TransitAnomalyPolicy.detect(
                List.of(route("CWB101", "101", 12, 30, 40, now)),
                List.of(),
                List.of(
                        arrival("CWB101", "101", "CWS001", "창원역", 5, now),
                        arrival("CWB101", "101", "CWS001", "창원역", 70, now)
                ),
                RULE,
                now,
                DayOfWeek.SATURDAY
        );

        assertThat(anomalies).singleElement()
                .satisfies(anomaly -> {
                    assertThat(anomaly.type()).isEqualTo("차량 간격 벌어짐");
                    assertThat(anomaly.baseline()).isEqualTo("토요일 기준 30분");
                    assertThat(anomaly.observed()).isEqualTo("도착 스냅샷 간격 65분");
                    assertThat(anomaly.metric()).isEqualTo("기준 대비 +35분");
                });
    }

    private static TransitAnomalyPolicy.RouteAnomalyCandidate route(
            String sourceRouteId,
            String routeNo,
            Integer weekdayIntervalMinutes,
            Integer saturdayIntervalMinutes,
            Integer sundayIntervalMinutes,
            Instant lastLocationCollectedAt
    ) {
        return new TransitAnomalyPolicy.RouteAnomalyCandidate(
                sourceRouteId,
                routeNo,
                weekdayIntervalMinutes,
                saturdayIntervalMinutes,
                sundayIntervalMinutes,
                "0500",
                "2300",
                lastLocationCollectedAt
        );
    }

    private static TransitAnomalyPolicy.LocationSnapshotCandidate location(
            String sourceRouteId,
            String routeNo,
            String vehicleNo,
            Instant collectedAt
    ) {
        return new TransitAnomalyPolicy.LocationSnapshotCandidate(
                sourceRouteId,
                routeNo,
                vehicleNo,
                "CWS001",
                1,
                35.1,
                128.1,
                collectedAt
        );
    }

    private static TransitAnomalyPolicy.ArrivalSnapshotCandidate arrival(
            String sourceRouteId,
            String routeNo,
            String sourceNodeId,
            String nodeName,
            int arrivalRemainingMinutes,
            Instant collectedAt
    ) {
        return new TransitAnomalyPolicy.ArrivalSnapshotCandidate(
                sourceRouteId,
                routeNo,
                sourceNodeId,
                nodeName,
                arrivalRemainingMinutes,
                collectedAt.plusSeconds(arrivalRemainingMinutes * 60L),
                collectedAt
        );
    }
}
