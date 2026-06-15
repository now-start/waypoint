package org.nowstart.waypoint.adapter.in.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.in.CollectReferenceDataUseCase;
import org.nowstart.waypoint.application.service.ReferenceDataCollectionState;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataInitializer implements ApplicationRunner {

    private final CollectReferenceDataUseCase collectReferenceDataUseCase;
    private final ReferenceDataCollectionState collectionState;

    @Override
    public void run(ApplicationArguments args) {
        collectionState.markStartupStarted();
        log.info("Starting startup TAGO reference data collection.");
        try {
            CollectionResult result = collectReferenceDataUseCase.collect();
            log.info(
                    "Finished startup TAGO reference data collection. status={}, rows={}, failures={}, message={}",
                    result.status(),
                    result.rowCount(),
                    result.failureCount(),
                    result.message()
            );
        } finally {
            collectionState.markStartupFinished();
        }
    }
}
