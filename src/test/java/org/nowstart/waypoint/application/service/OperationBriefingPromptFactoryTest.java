package org.nowstart.waypoint.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.AnomalyBriefingItem;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.AnomalySnapshot;

import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

class OperationBriefingPromptFactoryTest {

    private final OperationBriefingPromptFactory promptFactory = new OperationBriefingPromptFactory();

    @Test
    @DisplayName("이상징후가 없으면 정상 상태 브리핑 요청 프롬프트를 만든다")
    void buildUserPromptWithoutAnomalies() {
        String prompt = promptFactory.buildUserPrompt(List.of());

        then(prompt).contains("현재 이상징후가 없습니다.");
    }

    @Test
    @DisplayName("이상징후와 스냅샷 근거를 프롬프트에 포함한다")
    void buildUserPromptWithSnapshotEvidence() {
        AnomalyBriefingItem anomaly = new AnomalyBriefingItem(
                "위험",
                "101",
                "차량 간격 벌어짐",
                "창원역 -> 시청",
                "평일 기준 12분",
                "차량 간격 34분",
                "기준 대비 +22분",
                "연속 차량 간격이 크게 벌어졌습니다.",
                List.of(new AnomalySnapshot(
                        "2026-06-15T10:58:00Z",
                        "창원71자1042",
                        "창원역",
                        42,
                        "35.2571, 128.6051"
                ))
        );

        String prompt = promptFactory.buildUserPrompt(List.of(anomaly));

        then(prompt)
                .contains("노선: 101")
                .contains("원래 기준: 평일 기준 12분")
                .contains("스냅샷 관측값: 차량 간격 34분")
                .contains("차이: 기준 대비 +22분")
                .contains("스냅샷 근거:")
                .contains("차량: 창원71자1042")
                .contains("순번: 42");
    }
}
