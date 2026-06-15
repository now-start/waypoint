package org.nowstart.waypoint.adapter.in.startup;

import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.in.CollectReferenceDataUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReferenceDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataInitializer.class);

    private final CollectReferenceDataUseCase collectReferenceDataUseCase;
    private final ReferenceDataStartupState startupState;

    public ReferenceDataInitializer(
            CollectReferenceDataUseCase collectReferenceDataUseCase,
            ReferenceDataStartupState startupState
    ) {
        this.collectReferenceDataUseCase = collectReferenceDataUseCase;
        this.startupState = startupState;
    }

    @Override
    public void run(ApplicationArguments args) {
        startupState.markStarted();
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
            startupState.markFinished();
        }
    }
}
