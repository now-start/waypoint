package org.nowstart.waypoint.domain.transit;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TransitAnomalyPolicy {

    private TransitAnomalyPolicy() {
    }

    public static List<TransitAnomaly> detect(
            List<RouteAnomalyCandidate> routes,
            List<LocationSnapshotCandidate> locationSnapshots,
            List<ArrivalSnapshotCandidate> arrivalSnapshots,
            Rule rule,
            Instant now,
            DayOfWeek dayOfWeek
    ) {
        Map<String, RouteAnomalyCandidate> routeMap = routes.stream()
                .collect(LinkedHashMap::new, (map, route) -> map.put(route.sourceRouteId(), route), Map::putAll);
        List<TransitAnomaly> anomalies = new ArrayList<>();
        anomalies.addAll(findStaleLocationAnomalies(routes, locationSnapshots, rule, now, dayOfWeek));
        anomalies.addAll(findHeadwayAnomalies(routeMap, arrivalSnapshots, rule, now, dayOfWeek));
        return anomalies.stream()
                .sorted(Comparator.comparing(TransitAnomaly::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static List<TransitAnomaly> findStaleLocationAnomalies(
            List<RouteAnomalyCandidate> routes,
            List<LocationSnapshotCandidate> snapshots,
            Rule rule,
            Instant now,
            DayOfWeek dayOfWeek
    ) {
        Map<String, LocationSnapshotCandidate> latestSnapshotByRoute = snapshots.stream()
                .filter(snapshot -> snapshot.sourceRouteId() != null)
                .collect(LinkedHashMap::new, (map, snapshot) -> map.putIfAbsent(snapshot.sourceRouteId(), snapshot), Map::putAll);

        List<TransitAnomaly> anomalies = new ArrayList<>();
        for (RouteAnomalyCandidate route : routes) {
            Instant latestCollectedAt = Optional.ofNullable(latestSnapshotByRoute.get(route.sourceRouteId()))
                    .map(LocationSnapshotCandidate::collectedAt)
                    .orElse(route.lastLocationCollectedAt());
            if (latestCollectedAt == null) {
                continue;
            }

            Duration expectedCollectionInterval = RouteLocationCollectionPolicy.collectionInterval(route.toCollectionCandidate(), dayOfWeek);
            Duration staleThreshold = expectedCollectionInterval.plus(rule.locationStale());
            Duration observedDelay = Duration.between(latestCollectedAt, now);
            if (observedDelay.compareTo(staleThreshold) <= 0) {
                continue;
            }

            LocationSnapshotCandidate evidence = latestSnapshotByRoute.get(route.sourceRouteId());
            anomalies.add(new TransitAnomaly(
                    "stale-location-" + route.sourceRouteId(),
                    severity(observedDelay, staleThreshold.multipliedBy(2)),
                    routeNo(route),
                    "위치 갱신 지연",
                    route.sourceRouteId(),
                    "예상 위치 갱신 " + minutes(expectedCollectionInterval) + "분 주기",
                    "최근 위치 " + minutes(observedDelay) + "분 전",
                    "허용 기준 대비 +" + minutes(observedDelay.minus(staleThreshold)) + "분",
                    "노선의 요일별 배차 간격으로 계산한 위치 수집 기대 주기에 기본 지연 허용값 "
                            + minutes(rule.locationStale()) + "분을 더해 판정했습니다.",
                    latestCollectedAt,
                    evidence == null ? List.of() : List.of(evidence.toEvidence())
            ));
        }
        return anomalies;
    }

    private static List<TransitAnomaly> findHeadwayAnomalies(
            Map<String, RouteAnomalyCandidate> routeMap,
            List<ArrivalSnapshotCandidate> snapshots,
            Rule rule,
            Instant now,
            DayOfWeek dayOfWeek
    ) {
        Map<String, List<ArrivalSnapshotCandidate>> snapshotsByRouteStop = new LinkedHashMap<>();
        for (ArrivalSnapshotCandidate snapshot : snapshots) {
            if (!routeMap.containsKey(snapshot.sourceRouteId())) {
                continue;
            }
            String key = snapshot.sourceRouteId() + "|" + snapshot.sourceNodeId();
            snapshotsByRouteStop.computeIfAbsent(key, ignored -> new ArrayList<>()).add(snapshot);
        }

        List<TransitAnomaly> anomalies = new ArrayList<>();
        for (List<ArrivalSnapshotCandidate> routeStopSnapshots : snapshotsByRouteStop.values()) {
            List<ArrivalSnapshotCandidate> latestWindow = latestArrivalWindow(routeStopSnapshots);
            if (latestWindow.size() < 2) {
                continue;
            }

            ArrivalSnapshotCandidate first = latestWindow.getFirst();
            RouteAnomalyCandidate route = routeMap.get(first.sourceRouteId());
            Integer expectedHeadway = RouteLocationCollectionPolicy.headwayMinutes(route.toCollectionCandidate(), dayOfWeek);
            if (expectedHeadway == null) {
                continue;
            }

            List<ArrivalSnapshotCandidate> sortedByArrival = latestWindow.stream()
                    .sorted(Comparator.comparing(ArrivalSnapshotCandidate::arrivalRemainingMinutes))
                    .toList();
            int observedHeadway = Math.abs(sortedByArrival.get(1).arrivalRemainingMinutes()
                    - sortedByArrival.getFirst().arrivalRemainingMinutes());
            int wideThreshold = expectedHeadway + (int) rule.headwayWide().toMinutes();
            int narrowThreshold = (int) Math.min(expectedHeadway, rule.headwayNarrow().toMinutes());
            boolean isWide = observedHeadway >= wideThreshold;
            boolean isNarrow = observedHeadway <= narrowThreshold && expectedHeadway > observedHeadway;
            if (!isWide && !isNarrow) {
                continue;
            }

            String type = isWide ? "차량 간격 벌어짐" : "차량 간격 붙음";
            anomalies.add(new TransitAnomaly(
                    (isWide ? "wide-headway-" : "narrow-headway-") + first.sourceRouteId() + "-" + first.sourceNodeId(),
                    isWide ? "위험" : "주의",
                    routeNo(route),
                    type,
                    nullToDash(first.nodeName()),
                    dayLabel(dayOfWeek) + " 기준 " + expectedHeadway + "분",
                    "도착 스냅샷 간격 " + observedHeadway + "분",
                    isWide ? "기준 대비 +" + (observedHeadway - expectedHeadway) + "분"
                            : "기준 대비 -" + (expectedHeadway - observedHeadway) + "분",
                    "같은 노선과 정류소의 최신 도착 스냅샷에서 1번째와 2번째 도착 예정 시간 차이를 계산해 요일별 원래 배차와 비교했습니다.",
                    first.collectedAt() == null ? now : first.collectedAt(),
                    sortedByArrival.stream()
                            .limit(2)
                            .map(ArrivalSnapshotCandidate::toEvidence)
                            .toList()
            ));
        }
        return anomalies;
    }

    private static List<ArrivalSnapshotCandidate> latestArrivalWindow(List<ArrivalSnapshotCandidate> snapshots) {
        Instant latestCollectedAt = snapshots.stream()
                .map(ArrivalSnapshotCandidate::collectedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latestCollectedAt == null) {
            return List.of();
        }
        Instant lowerBound = latestCollectedAt.minus(Duration.ofMinutes(2));
        return snapshots.stream()
                .filter(snapshot -> snapshot.collectedAt() != null)
                .filter(snapshot -> !snapshot.collectedAt().isBefore(lowerBound))
                .toList();
    }

    private static String severity(Duration observed, Duration dangerThreshold) {
        return observed.compareTo(dangerThreshold) >= 0 ? "위험" : "주의";
    }

    private static String routeNo(RouteAnomalyCandidate route) {
        return nullToDash(route.routeNo());
    }

    private static String dayLabel(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
            default -> "평일";
        };
    }

    private static long minutes(Duration duration) {
        return Math.max(0, duration.toMinutes());
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record Rule(
            Duration headwayWide,
            Duration headwayNarrow,
            Duration locationStale
    ) {
    }

    public record RouteAnomalyCandidate(
            String sourceRouteId,
            String routeNo,
            Integer weekdayIntervalMinutes,
            Integer saturdayIntervalMinutes,
            Integer sundayIntervalMinutes,
            String firstVehicleTime,
            String lastVehicleTime,
            Instant lastLocationCollectedAt
    ) {

        RouteCollectionCandidate toCollectionCandidate() {
            return new RouteCollectionCandidate(
                    sourceRouteId,
                    weekdayIntervalMinutes,
                    saturdayIntervalMinutes,
                    sundayIntervalMinutes,
                    firstVehicleTime,
                    lastVehicleTime,
                    lastLocationCollectedAt
            );
        }
    }

    public record LocationSnapshotCandidate(
            String sourceRouteId,
            String routeNo,
            String vehicleNo,
            String sourceNodeId,
            Integer nodeOrder,
            Double gpsLatitude,
            Double gpsLongitude,
            Instant collectedAt
    ) {

        TransitAnomalyEvidence toEvidence() {
            return new TransitAnomalyEvidence(
                    collectedAt,
                    nullToDash(vehicleNo),
                    nullToDash(sourceNodeId),
                    nodeOrder,
                    gps(gpsLatitude, gpsLongitude)
            );
        }
    }

    public record ArrivalSnapshotCandidate(
            String sourceRouteId,
            String routeNo,
            String sourceNodeId,
            String nodeName,
            Integer arrivalRemainingMinutes,
            Instant arrivalExpectedAt,
            Instant collectedAt
    ) {

        TransitAnomalyEvidence toEvidence() {
            return new TransitAnomalyEvidence(
                    collectedAt,
                    "-",
                    nullToDash(nodeName),
                    arrivalRemainingMinutes,
                    arrivalExpectedAt == null ? "-" : "예상도착 " + arrivalExpectedAt
            );
        }
    }

    public record TransitAnomaly(
            String id,
            String severity,
            String routeNo,
            String type,
            String area,
            String baseline,
            String observed,
            String metric,
            String reason,
            Instant updatedAt,
            List<TransitAnomalyEvidence> snapshots
    ) {
    }

    public record TransitAnomalyEvidence(
            Instant collectedAt,
            String vehicleNo,
            String nodeName,
            Integer nodeOrder,
            String gps
    ) {
    }

    private static String gps(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return "-";
        }
        return latitude + ", " + longitude;
    }
}
