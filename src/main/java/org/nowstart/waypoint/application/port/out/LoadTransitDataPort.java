package org.nowstart.waypoint.application.port.out;

import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadTransitDataPort {

    List<RouteReference> loadRoutes(String cityCode);

    List<StopReference> loadStops(String cityCode);

    List<LocationSnapshotReference> loadRecentLocationSnapshots(String cityCode, Instant since, int limit);

    List<ArrivalSnapshotReference> loadRecentArrivalSnapshots(String cityCode, Instant since, int limit);

    Optional<Instant> latestLocationCollectedAt();

    Optional<Instant> latestArrivalCollectedAt();

    CollectionStatusSnapshot loadCollectionStatus(int recentRunLimit);

    List<CollectionRunSnapshot> loadRecentRuns(int limit);

    record RouteReference(
            String cityCode,
            String sourceRouteId,
            String routeNo,
            Integer weekdayIntervalMinutes,
            Integer saturdayIntervalMinutes,
            Integer sundayIntervalMinutes,
            String firstVehicleTime,
            String lastVehicleTime,
            Instant lastLocationCollectedAt
    ) {
    }

    record StopReference(
            String cityCode,
            String sourceNodeId,
            String nodeName,
            Instant lastArrivalCollectedAt
    ) {
    }

    record LocationSnapshotReference(
            String sourceRouteId,
            String routeNo,
            String vehicleNo,
            String sourceNodeId,
            Integer nodeOrder,
            Double gpsLatitude,
            Double gpsLongitude,
            Instant collectedAt
    ) {
    }

    record ArrivalSnapshotReference(
            String sourceRouteId,
            String routeNo,
            String sourceNodeId,
            String nodeName,
            Integer arrivalRemainingMinutes,
            Instant arrivalExpectedAt,
            Instant collectedAt
    ) {
    }

    record CollectionStatusSnapshot(
            long routeCount,
            long stopCount,
            long routeStopCount,
            long locationSnapshotCount,
            long arrivalSnapshotCount,
            Instant latestLocationCollectedAt,
            Instant latestArrivalCollectedAt,
            List<CollectionRunSnapshot> recentRuns
    ) {
    }

    record CollectionRunSnapshot(
            Long id,
            CollectionApiType apiType,
            CollectionStatus status,
            String requestKey,
            int rowCount,
            String resultCode,
            String resultMessage,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
