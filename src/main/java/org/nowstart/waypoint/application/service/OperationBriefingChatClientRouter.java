package org.nowstart.waypoint.application.service;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.OperationBriefingOptions;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.ProviderOption;
import org.nowstart.waypoint.config.OperationBriefingAiProperties;
import org.nowstart.waypoint.config.OperationBriefingAiProperties.Provider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
class OperationBriefingChatClientRouter {

    private static final int MAX_MODEL_LENGTH = 80;
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@+-]{0,79}");

    private final OperationBriefingAiProperties properties;
    private final ObservationRegistry observationRegistry;

    SelectedChatClient select(String providerValue, String modelValue) {
        Provider provider = parseProvider(providerValue);
        String model = resolveModel(provider, modelValue);
        return new SelectedChatClient(provider, model, createClient(provider, model));
    }

    OperationBriefingOptions options() {
        List<ProviderOption> providers = Arrays.stream(Provider.values())
                .map((provider) -> new ProviderOption(providerValue(provider), providerLabel(provider), configuredModel(provider)))
                .toList();
        Provider defaultProvider = properties.getDefaultProvider();
        return new OperationBriefingOptions(defaultProvider == null ? "" : providerValue(defaultProvider), providers);
    }

    private Provider parseProvider(String value) {
        if (!StringUtils.hasText(value)) {
            Provider defaultProvider = properties.getDefaultProvider();
            if (defaultProvider == null) {
                throw new IllegalStateException("waypoint.briefing.ai.default-provider is required.");
            }
            return defaultProvider;
        }
        try {
            return Provider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported AI briefing provider: " + value.trim(), ex);
        }
    }

    private String resolveModel(Provider provider, String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return validateModelName(requestedModel.trim());
        }
        return validateModelName(requireText(configuredModel(provider), "waypoint.briefing.ai." + providerValue(provider) + ".model"));
    }

    private String configuredModel(Provider provider) {
        return switch (provider) {
            case OLLAMA -> properties.getOllama().getModel();
            case OPENAI -> properties.getOpenai().getModel();
        };
    }

    private ChatClient createClient(Provider provider, String model) {
        return switch (provider) {
            case OLLAMA -> ChatClient.create(createOllamaModel(model));
            case OPENAI -> ChatClient.create(createOpenAiModel(model));
        };
    }

    private OllamaChatModel createOllamaModel(String model) {
        OperationBriefingAiProperties.Ollama ollama = properties.getOllama();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(model)
                .temperature(ollama.getTemperature())
                .topP(ollama.getTopP())
                .topK(ollama.getTopK())
                .repeatPenalty(ollama.getRepeatPenalty())
                .numCtx(ollama.getNumCtx())
                .numPredict(ollama.getNumPredict())
                .keepAlive(ollama.getKeepAlive())
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl(requireText(ollama.getBaseUrl(), "waypoint.briefing.ai.ollama.base-url"))
                        .build())
                .options(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    private OpenAiChatModel createOpenAiModel(String model) {
        OperationBriefingAiProperties.OpenAi openai = properties.getOpenai();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(requireText(openai.getApiKey(), "OPENAI_API_KEY"))
                .baseUrl(requireText(openai.getBaseUrl(), "waypoint.briefing.ai.openai.base-url"))
                .model(model)
                .temperature(openai.getTemperature())
                .topP(openai.getTopP())
                .maxTokens(openai.getNumPredict())
                .build();
        return OpenAiChatModel.builder()
                .options(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    private static String providerValue(Provider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    private static String providerLabel(Provider provider) {
        return switch (provider) {
            case OLLAMA -> "Ollama";
            case OPENAI -> "OpenAI";
        };
    }

    private static String validateModelName(String model) {
        if (model.length() > MAX_MODEL_LENGTH || !MODEL_NAME_PATTERN.matcher(model).matches()) {
            throw new IllegalArgumentException("AI briefing model must be 1-80 chars using letters, digits, '.', '_', ':', '/', '@', '+', or '-'.");
        }
        return model;
    }

    private static String requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is required.");
        }
        return value.trim();
    }

    record SelectedChatClient(Provider provider, String model, ChatClient client) {
    }
}
