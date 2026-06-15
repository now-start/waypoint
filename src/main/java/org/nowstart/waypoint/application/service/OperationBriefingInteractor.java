package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationBriefingInteractor implements GenerateOperationBriefingUseCase {

    private final ChatClient.Builder chatClientBuilder;
    private final OperationBriefingPromptFactory promptFactory = new OperationBriefingPromptFactory();

    @Override
    public OperationBriefing generate(OperationBriefingCommand command) {
        String userPrompt = promptFactory.buildUserPrompt(command == null ? null : command.anomalies());
        String content = chatClientBuilder.build()
                .prompt()
                .system(OperationBriefingPromptFactory.SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        return new OperationBriefing(content);
    }
}
