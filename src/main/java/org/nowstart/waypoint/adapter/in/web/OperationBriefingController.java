package org.nowstart.waypoint.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.OperationBriefingCommand;

@RestController
@RequestMapping("/api/briefings")
@RequiredArgsConstructor
public class OperationBriefingController {

    private final GenerateOperationBriefingUseCase generateOperationBriefingUseCase;

    @PostMapping("/operations")
    public GenerateOperationBriefingUseCase.OperationBriefing createOperationBriefing(
            @RequestBody OperationBriefingCommand request
    ) {
        return generateOperationBriefingUseCase.generate(request);
    }
}
