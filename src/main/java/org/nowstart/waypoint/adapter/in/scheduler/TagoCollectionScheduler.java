package org.nowstart.waypoint.adapter.in.scheduler;

import org.nowstart.waypoint.adapter.in.startup.ReferenceDataStartupState;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TagoCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TagoCollectionScheduler.class);

    private final CollectBusLocationUseCase collectBusLocationUseCase;
    private final ReferenceDataStartupState referenceDataStartupState;
    private final AtomicBoolean startupSkipLogged = new AtomicBoolean(false);

    public TagoCollectionScheduler(
            CollectBusLocationUseCase collectBusLocationUseCase,
            ReferenceDataStartupState referenceDataStartupState
    ) {
        this.collectBusLocationUseCase = collectBusLocationUseCase;
        this.referenceDataStartupState = referenceDataStartupState;
    }

    @Scheduled(
            fixedDelayString = "${waypoint.tago.collection.location-fixed-delay:PT1M}",
            initialDelayString = "${waypoint.tago.collection.location-fixed-delay:PT1M}"
    )
    public void collectLocations() {
        if (referenceDataStartupState.isRunning()) {
            if (startupSkipLogged.compareAndSet(false, true)) {
                log.info("Skipping scheduled TAGO bus location collection because startup reference data collection is still running.");
            } else {
                log.debug("Skipping scheduled TAGO bus location collection because startup reference data collection is still running.");
            }
            return;
        }
        startupSkipLogged.set(false);
        log.info("Starting scheduled TAGO bus location collection.");
        CollectionResult result = collectBusLocationUseCase.collect();
        log.info(
                "Finished scheduled TAGO bus location collection. status={}, rows={}, failures={}, message={}",
                result.status(),
                result.rowCount(),
                result.failureCount(),
                result.message()
        );
    }
}
