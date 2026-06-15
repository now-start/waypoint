package org.nowstart.waypoint.application.port.in;

import java.time.Instant;
import java.util.List;

public interface QueryTransitAnomalyUseCase {

    List<TransitAnomaly> queryAnomalies();

    record TransitAnomaly(
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
            List<TransitAnomalySnapshot> snapshots
    ) {
    }

    record TransitAnomalySnapshot(
            Instant collectedAt,
            String vehicleNo,
            String nodeName,
            Integer nodeOrder,
            String gps
    ) {
    }
}
