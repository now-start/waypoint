package org.nowstart.waypoint.domain.transit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

class RouteLocationCollectionPolicyTest {

    private static final Instant NOON = Instant.parse("2026-06-15T03:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("배차간격이 짧은 노선일수록 위치 수집 주기를 짧게 잡는다")
    void collectionIntervalUsesHeadwayBuckets() {
        then(RouteLocationCollectionPolicy.collectionInterval(route("8", 8, null), DayOfWeek.MONDAY))
                .isEqualTo(Duration.ofMinutes(8));
        then(RouteLocationCollectionPolicy.collectionInterval(route("20", 20, null), DayOfWeek.MONDAY))
                .isEqualTo(Duration.ofMinutes(12));
        then(RouteLocationCollectionPolicy.collectionInterval(route("50", 50, null), DayOfWeek.MONDAY))
                .isEqualTo(Duration.ofMinutes(20));
        then(RouteLocationCollectionPolicy.collectionInterval(route("90", 90, null), DayOfWeek.MONDAY))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("요일에 맞는 배차간격으로 위치 수집 주기를 계산한다")
    void collectionIntervalUsesDaySpecificHeadway() {
        RouteCollectionCandidate route = new RouteCollectionCandidate(
                "CWB101",
                20,
                50,
                90,
                "0500",
                "2330",
                null
        );

        then(RouteLocationCollectionPolicy.collectionInterval(route, DayOfWeek.MONDAY))
                .isEqualTo(Duration.ofMinutes(12));
        then(RouteLocationCollectionPolicy.collectionInterval(route, DayOfWeek.SATURDAY))
                .isEqualTo(Duration.ofMinutes(20));
        then(RouteLocationCollectionPolicy.collectionInterval(route, DayOfWeek.SUNDAY))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("최근 수집 시도 시각이 노선별 주기보다 오래된 운행 노선만 due로 선택한다")
    void selectDueRoutesUsesLastAttemptedAtAndOperationWindow() {
        RouteCollectionCandidate dueShortRoute = route(
                "8",
                8,
                NOON.minus(Duration.ofMinutes(8))
        );
        RouteCollectionCandidate notDueShortRoute = route(
                "9",
                8,
                NOON.minus(Duration.ofMinutes(7))
        );
        RouteCollectionCandidate inactiveRoute = new RouteCollectionCandidate(
                "closed",
                8,
                8,
                8,
                "2300",
                "2330",
                null
        );

        RouteLocationCollectionPolicy.Selection selection = RouteLocationCollectionPolicy.selectDueRoutes(
                List.of(dueShortRoute, notDueShortRoute, inactiveRoute),
                NOON,
                SEOUL
        );

        then(selection.dueSourceRouteIds()).containsExactly("CWB8");
        then(selection.skippedInactiveRoutes()).isEqualTo(1);
        then(selection.skippedNotDueRoutes()).isEqualTo(1);
    }

    @Test
    @DisplayName("노선 운행 시간 판정은 첫차와 막차 사이에만 활성으로 본다")
    void routeOperationWindowActiveOnlyBetweenFirstAndLastVehicleTime() {
        RouteCollectionCandidate route = new RouteCollectionCandidate(
                "CWB101",
                20,
                20,
                20,
                "0520",
                "2310",
                null
        );

        then(RouteOperationWindow.isActive(route, LocalTime.of(5, 20))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(12, 0))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 10))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(4, 59))).isFalse();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 11))).isFalse();
    }

    @Test
    @DisplayName("막차가 자정을 넘는 노선은 날짜 경계를 넘어 운행 중으로 본다")
    void routeOperationWindowSupportsOvernightRoutes() {
        RouteCollectionCandidate route = new RouteCollectionCandidate(
                "CWB900",
                20,
                20,
                20,
                "2330",
                "0110",
                null
        );

        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 40))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(0, 30))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(2, 0))).isFalse();
    }

    @Test
    @DisplayName("첫차와 막차 시간이 없으면 기본 운행 시간 05:00부터 23:30까지로 본다")
    void routeOperationWindowUsesDefaultWindowWhenRouteTimesAreMissing() {
        RouteCollectionCandidate route = new RouteCollectionCandidate(
                "CWB101",
                20,
                20,
                20,
                null,
                null,
                null
        );

        then(RouteOperationWindow.isActive(route, LocalTime.of(5, 0))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 30))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(4, 59))).isFalse();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 31))).isFalse();
    }

    @Test
    @DisplayName("첫차와 막차 중 하나만 없으면 기본 운행 시간 전체를 사용한다")
    void routeOperationWindowUsesDefaultWindowWhenEitherRouteTimeIsMissing() {
        RouteCollectionCandidate route = new RouteCollectionCandidate(
                "CWB101",
                20,
                20,
                20,
                "2330",
                null,
                null
        );

        then(RouteOperationWindow.isActive(route, LocalTime.of(12, 0))).isTrue();
        then(RouteOperationWindow.isActive(route, LocalTime.of(23, 31))).isFalse();
    }

    private static RouteCollectionCandidate route(String routeNo, Integer weekdayInterval, Instant lastAttemptedAt) {
        return new RouteCollectionCandidate(
                "CWB" + routeNo,
                weekdayInterval,
                weekdayInterval,
                weekdayInterval,
                "0500",
                "2330",
                lastAttemptedAt
        );
    }
}
