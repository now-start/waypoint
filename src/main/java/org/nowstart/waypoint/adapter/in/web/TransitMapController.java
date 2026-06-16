package org.nowstart.waypoint.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.QueryTransitMapUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/map")
@RequiredArgsConstructor
public class TransitMapController {

    private final QueryTransitMapUseCase queryTransitMapUseCase;

    @GetMapping
    public QueryTransitMapUseCase.TransitMapView getMap() {
        return queryTransitMapUseCase.getMap();
    }
}
