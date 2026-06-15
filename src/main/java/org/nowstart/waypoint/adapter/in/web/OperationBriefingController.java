package org.nowstart.waypoint.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/briefings")
@RequiredArgsConstructor
public class OperationBriefingController {

    private final ChatClient.Builder chatClientBuilder;

    @PostMapping("/operations")
    public OperationBriefingResponse createOperationBriefing(@RequestBody OperationBriefingRequest request) {
        String prompt = buildPrompt(request.anomalies());
        String content = chatClientBuilder.build()
                .prompt()
                .system("""
                        당신은 버스 운영 콘솔의 AI 브리핑 작성자입니다.
                        서버가 제공한 구조화된 이상징후와 스냅샷 근거만 사용하세요.
                        원인을 단정하지 말고, 가능성/확인 필요 표현을 사용하세요.
                        운영자가 바로 확인할 수 있게 3~5문장 한국어로 간결하게 작성하세요.
                        """)
                .user(prompt)
                .call()
                .content();

        return new OperationBriefingResponse(content);
    }

    private static String buildPrompt(List<AnomalyBriefingItem> anomalies) {
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
            if (anomaly.snapshots() != null && !anomaly.snapshots().isEmpty()) {
                builder.append("  스냅샷 근거:\n");
                for (AnomalySnapshot snapshot : anomaly.snapshots()) {
                    builder.append("  * 수집시각: ").append(value(snapshot.collectedAt()))
                            .append(", 차량: ").append(value(snapshot.vehicleNo()))
                            .append(", 정류소: ").append(value(snapshot.nodeName()))
                            .append(", 순번: ").append(value(snapshot.nodeOrder()))
                            .append(", 좌표: ").append(value(snapshot.gps()))
                            .append('\n');
                }
            }
        }
        return builder.toString();
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }

    public record OperationBriefingRequest(List<AnomalyBriefingItem> anomalies) {
    }

    public record AnomalyBriefingItem(
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

    public record AnomalySnapshot(
            String collectedAt,
            String vehicleNo,
            String nodeName,
            Integer nodeOrder,
            String gps
    ) {
    }

    public record OperationBriefingResponse(String content) {
    }
}
