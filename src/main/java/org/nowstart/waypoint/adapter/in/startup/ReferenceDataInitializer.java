package org.nowstart.waypoint.adapter.in.startup;

import org.nowstart.waypoint.application.port.in.CollectReferenceDataUseCase;
import org.nowstart.waypoint.config.CollectionProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReferenceDataInitializer implements ApplicationRunner {

    private final CollectionProperties collectionProperties;
    private final CollectReferenceDataUseCase collectReferenceDataUseCase;

    public ReferenceDataInitializer(
            CollectionProperties collectionProperties,
            CollectReferenceDataUseCase collectReferenceDataUseCase
    ) {
        this.collectionProperties = collectionProperties;
        this.collectReferenceDataUseCase = collectReferenceDataUseCase;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (collectionProperties.referenceDataOnStartup()) {
            collectReferenceDataUseCase.collect();
        }
    }
}
