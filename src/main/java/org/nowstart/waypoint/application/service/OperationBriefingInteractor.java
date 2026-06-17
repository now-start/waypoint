package org.nowstart.waypoint.application.service;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationBriefingInteractor implements GenerateOperationBriefingUseCase {

    private final OperationBriefingChatClientRouter chatClientRouter;
    private final OperationBriefingPromptFactory promptFactory = new OperationBriefingPromptFactory();

    @Override
    public OperationBriefing generate(OperationBriefingCommand command) {
        String userPrompt = promptFactory.buildUserPrompt(command == null ? null : command.anomalies());
        OperationBriefingChatClientRouter.SelectedChatClient selected = chatClientRouter.select(
                command == null ? null : command.provider(),
                command == null ? null : command.model()
        );
        String content = selected.client()
                .prompt()
                .system(OperationBriefingPromptFactory.SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        return new OperationBriefing(content);
    }

    @Override
    public OperationBriefingOptions options() {
        return chatClientRouter.options();
    }
}
