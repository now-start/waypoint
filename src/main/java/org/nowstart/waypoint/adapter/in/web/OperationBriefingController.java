package org.nowstart.waypoint.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.nowstart.waypoint.application.port.in.GenerateOperationBriefingUseCase.OperationBriefingCommand;

@RestController
@RequestMapping("/api/briefings")
@RequiredArgsConstructor
public class OperationBriefingController {

    private final GenerateOperationBriefingUseCase generateOperationBriefingUseCase;

    @GetMapping("/options")
    public GenerateOperationBriefingUseCase.OperationBriefingOptions operationBriefingOptions() {
        return generateOperationBriefingUseCase.options();
    }

    @PostMapping("/operations")
    public GenerateOperationBriefingUseCase.OperationBriefing createOperationBriefing(
            @RequestBody OperationBriefingCommand request
    ) {
        try {
            return generateOperationBriefingUseCase.generate(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
