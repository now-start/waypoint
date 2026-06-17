package org.nowstart.waypoint.application.port.in;

import java.util.List;

public interface GenerateOperationBriefingUseCase {

    OperationBriefing generate(OperationBriefingCommand command);

    OperationBriefingOptions options();

    record OperationBriefingCommand(String provider, String model, List<AnomalyBriefingItem> anomalies) {
    }

    record OperationBriefingOptions(String defaultProvider, List<ProviderOption> providers) {
    }

    record ProviderOption(String provider, String label, String defaultModel) {
    }

    record AnomalyBriefingItem(
            String severity,
            String routeNo,
            String type,
            String area,
            String baseline,
            String observed,
            String metric,
            String reason,
            List<AnomalySnapshot> snapshots
    ) {
    }

    record AnomalySnapshot(
            String collectedAt,
            String vehicleNo,
            String nodeName,
            Integer nodeOrder,
            String gps
    ) {
    }

    record OperationBriefing(String content) {
    }
}
