package org.nowstart.waypoint.adapter.in.scheduler;

import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.config.CollectionProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TagoCollectionScheduler {

    private final CollectionProperties collectionProperties;
    private final CollectBusLocationUseCase collectBusLocationUseCase;

    public TagoCollectionScheduler(
            CollectionProperties collectionProperties,
            CollectBusLocationUseCase collectBusLocationUseCase
    ) {
        this.collectionProperties = collectionProperties;
        this.collectBusLocationUseCase = collectBusLocationUseCase;
    }

    @Scheduled(fixedDelayString = "${waypoint.collection.location-fixed-delay:PT1M}")
    public void collectLocations() {
        if (collectionProperties.locationSchedulerEnabled()) {
            collectBusLocationUseCase.collect();
        }
    }
}
