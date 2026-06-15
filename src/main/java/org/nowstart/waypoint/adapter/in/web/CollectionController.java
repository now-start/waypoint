package org.nowstart.waypoint.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.nowstart.waypoint.application.port.in.CollectBusArrivalUseCase;
import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.application.port.in.CollectReferenceDataUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.in.QueryCollectionStatusUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectReferenceDataUseCase collectReferenceDataUseCase;
    private final CollectBusLocationUseCase collectBusLocationUseCase;
    private final CollectBusArrivalUseCase collectBusArrivalUseCase;
    private final QueryCollectionStatusUseCase queryCollectionStatusUseCase;

    @PostMapping("/reference-data")
    public CollectionResult collectReferenceData() {
        return collectReferenceDataUseCase.collect();
    }

    @PostMapping("/locations")
    public CollectionResult collectLocations() {
        return collectBusLocationUseCase.collect();
    }

    @PostMapping("/arrivals")
    public CollectionResult collectArrivals() {
        return collectBusArrivalUseCase.collectAllStops();
    }

    @GetMapping("/status")
    public QueryCollectionStatusUseCase.CollectionStatusView getStatus() {
        return queryCollectionStatusUseCase.getStatus();
    }

    @GetMapping("/runs")
    public List<QueryCollectionStatusUseCase.CollectionRunView> getRecentRuns(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return queryCollectionStatusUseCase.getRecentRuns(limit);
    }
}
