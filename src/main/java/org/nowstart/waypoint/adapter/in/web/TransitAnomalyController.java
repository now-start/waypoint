package org.nowstart.waypoint.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.QueryTransitAnomalyUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class TransitAnomalyController {

    private final QueryTransitAnomalyUseCase queryTransitAnomalyUseCase;

    @GetMapping
    public List<QueryTransitAnomalyUseCase.TransitAnomaly> getAnomalies() {
        return queryTransitAnomalyUseCase.queryAnomalies();
    }
}
