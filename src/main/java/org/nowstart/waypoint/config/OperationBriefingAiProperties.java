package org.nowstart.waypoint.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "waypoint.briefing.ai")
public class OperationBriefingAiProperties {

    private Provider defaultProvider;
    private Ollama ollama = new Ollama();
    private OpenAi openai = new OpenAi();

    public enum Provider {
        OLLAMA,
        OPENAI
    }

    @Getter
    @Setter
    public static class Ollama {
        private String baseUrl;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double repeatPenalty;
        private Integer numCtx;
        private Integer numPredict;
        private String keepAlive;
    }

    @Getter
    @Setter
    public static class OpenAi {
        private String apiKey;
        private String baseUrl;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer numPredict;
    }
}
