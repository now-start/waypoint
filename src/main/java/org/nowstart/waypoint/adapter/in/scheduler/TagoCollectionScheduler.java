package org.nowstart.waypoint.adapter.in.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.application.service.ReferenceDataCollectionState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class TagoCollectionScheduler {

    private final CollectBusLocationUseCase collectBusLocationUseCase;
    private final ReferenceDataCollectionState referenceDataCollectionState;
    private final AtomicBoolean startupSkipLogged = new AtomicBoolean(false);

    @Scheduled(
            fixedDelayString = "${waypoint.tago.collection.location-fixed-delay:PT1M}",
            initialDelayString = "${waypoint.tago.collection.location-initial-delay:PT0S}"
    )
    public void collectLocations() {
        if (referenceDataCollectionState.shouldDeferLocationCollection()) {
            if (startupSkipLogged.compareAndSet(false, true)) {
                log.info("Skipping scheduled TAGO bus location collection until startup route reference data is ready.");
            } else {
                log.debug("Skipping scheduled TAGO bus location collection until startup route reference data is ready.");
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
