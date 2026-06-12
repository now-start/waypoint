package org.nowstart.waypoint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waypoint.ollama")
public record OllamaProperties(
        String baseUrl,
        String model
) {

    public OllamaProperties {
        baseUrl = hasText(baseUrl) ? trimTrailingSlash(baseUrl) : "http://localhost:11434";
        model = hasText(model) ? model.trim() : "llama3.1";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
