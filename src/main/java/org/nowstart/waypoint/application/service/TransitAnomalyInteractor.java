package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.QueryTransitAnomalyUseCase;
import org.nowstart.waypoint.application.port.out.LoadTransitDataPort;
import org.nowstart.waypoint.config.AnomalyProperties;
import org.nowstart.waypoint.domain.transit.TransitAnomalyPolicy;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransitAnomalyInteractor implements QueryTransitAnomalyUseCase {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int SNAPSHOT_LOOKBACK_MULTIPLIER = 3;
    private static final int RECENT_SNAPSHOT_LIMIT = 2_000;
    private static final int MAX_ANOMALIES = 30;

    private final LoadTransitDataPort loadTransitDataPort;
    private final TagoCityCodeResolver cityCodeResolver;
    private final AnomalyProperties anomalyProperties;

    @Override
    public List<TransitAnomaly> queryAnomalies() {
        String cityCode = cityCodeResolver.resolve();
        Instant now = Instant.now();
        DayOfWeek dayOfWeek = now.atZone(SERVICE_ZONE).getDayOfWeek();
        Duration lookback = anomalyProperties.headwayWide().multipliedBy(SNAPSHOT_LOOKBACK_MULTIPLIER);

        List<TransitAnomalyPolicy.TransitAnomaly> anomalies = TransitAnomalyPolicy.detect(
                loadTransitDataPort.loadRoutes(cityCode).stream()
                        .map(this::toRouteCandidate)
                        .toList(),
                loadTransitDataPort.loadRecentLocationSnapshots(cityCode, now.minus(lookback), RECENT_SNAPSHOT_LIMIT).stream()
                        .map(this::toLocationCandidate)
                        .toList(),
                loadTransitDataPort.loadRecentArrivalSnapshots(cityCode, now.minus(lookback), RECENT_SNAPSHOT_LIMIT).stream()
                        .map(this::toArrivalCandidate)
                        .toList(),
                new TransitAnomalyPolicy.Rule(
                        anomalyProperties.headwayWide(),
                        anomalyProperties.headwayNarrow(),
                        anomalyProperties.locationStale()
                ),
                now,
                dayOfWeek
        );

        return anomalies.stream()
                .limit(MAX_ANOMALIES)
                .map(this::toResponse)
                .toList();
    }

    private TransitAnomalyPolicy.RouteAnomalyCandidate toRouteCandidate(LoadTransitDataPort.RouteReference route) {
        return new TransitAnomalyPolicy.RouteAnomalyCandidate(
                route.sourceRouteId(),
                route.routeNo(),
                route.weekdayIntervalMinutes(),
                route.saturdayIntervalMinutes(),
                route.sundayIntervalMinutes(),
                route.firstVehicleTime(),
                route.lastVehicleTime(),
                route.lastLocationCollectedAt()
        );
    }

    private TransitAnomalyPolicy.LocationSnapshotCandidate toLocationCandidate(
            LoadTransitDataPort.LocationSnapshotReference snapshot
    ) {
        return new TransitAnomalyPolicy.LocationSnapshotCandidate(
                snapshot.sourceRouteId(),
                snapshot.routeNo(),
                snapshot.vehicleNo(),
                snapshot.sourceNodeId(),
                snapshot.nodeOrder(),
                snapshot.gpsLatitude(),
                snapshot.gpsLongitude(),
                snapshot.collectedAt()
        );
    }

    private TransitAnomalyPolicy.ArrivalSnapshotCandidate toArrivalCandidate(
            LoadTransitDataPort.ArrivalSnapshotReference snapshot
    ) {
        return new TransitAnomalyPolicy.ArrivalSnapshotCandidate(
                snapshot.sourceRouteId(),
                snapshot.routeNo(),
                snapshot.sourceNodeId(),
                snapshot.nodeName(),
                snapshot.arrivalRemainingMinutes(),
                snapshot.arrivalExpectedAt(),
                snapshot.collectedAt()
        );
    }

    private TransitAnomaly toResponse(TransitAnomalyPolicy.TransitAnomaly anomaly) {
        return new TransitAnomaly(
                anomaly.id(),
                anomaly.severity(),
                anomaly.routeNo(),
                anomaly.type(),
                anomaly.area(),
                anomaly.baseline(),
                anomaly.observed(),
                anomaly.metric(),
                anomaly.reason(),
                anomaly.updatedAt(),
                anomaly.snapshots().stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    private TransitAnomalySnapshot toResponse(TransitAnomalyPolicy.TransitAnomalyEvidence snapshot) {
        return new TransitAnomalySnapshot(
                snapshot.collectedAt(),
                snapshot.vehicleNo(),
                snapshot.nodeName(),
                snapshot.nodeOrder(),
                snapshot.gps()
        );
    }
}
