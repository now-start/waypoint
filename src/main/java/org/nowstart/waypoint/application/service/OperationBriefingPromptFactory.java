package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.AnomalyBriefingItem;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.AnomalySnapshot;

import java.util.List;

final class OperationBriefingPromptFactory {

    static final String SYSTEM_PROMPT = """
            당신은 버스 운영 콘솔의 AI 브리핑 작성자입니다.
            서버가 제공한 구조화된 이상징후와 스냅샷 근거만 사용하세요.
            원인을 단정하지 말고, 가능성/확인 필요 표현을 사용하세요.
            운영자가 바로 확인할 수 있게 한국어 2문장 이내, 120자 안팎으로 작성하세요.
            화면 상단의 좁은 브리핑 카드에 들어가므로 줄바꿈 목록이나 긴 설명은 쓰지 마세요.
            """;

    String buildUserPrompt(List<AnomalyBriefingItem> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) {
            return "현재 이상징후가 없습니다. 운영자가 읽을 짧은 정상 상태 브리핑을 작성하세요.";
        }

        StringBuilder builder = new StringBuilder("다음 이상징후를 근거로 운영 브리핑을 작성하세요.\n");
        for (AnomalyBriefingItem anomaly : anomalies) {
            builder.append("- 노선: ").append(value(anomaly.routeNo()))
                    .append(", 심각도: ").append(value(anomaly.severity()))
                    .append(", 유형: ").append(value(anomaly.type()))
                    .append(", 영향 구간: ").append(value(anomaly.area()))
                    .append(", 원래 기준: ").append(value(anomaly.baseline()))
                    .append(", 스냅샷 관측값: ").append(value(anomaly.observed()))
                    .append(", 차이: ").append(value(anomaly.metric()))
                    .append(", 판정 사유: ").append(value(anomaly.reason()))
                    .append('\n');
            appendSnapshots(builder, anomaly.snapshots());
        }
        return builder.toString();
    }

    private static void appendSnapshots(StringBuilder builder, List<AnomalySnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        builder.append("  스냅샷 근거:\n");
        for (AnomalySnapshot snapshot : snapshots) {
            builder.append("  * 수집시각: ").append(value(snapshot.collectedAt()))
                    .append(", 차량: ").append(value(snapshot.vehicleNo()))
                    .append(", 정류소: ").append(value(snapshot.nodeName()))
                    .append(", 순번: ").append(value(snapshot.nodeOrder()))
                    .append(", 좌표: ").append(value(snapshot.gps()))
                    .append('\n');
        }
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }
}
