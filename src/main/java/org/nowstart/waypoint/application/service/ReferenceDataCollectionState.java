package org.nowstart.waypoint.application.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ReferenceDataCollectionState {

    private final AtomicBoolean startupRunning = new AtomicBoolean(true);
    private final AtomicBoolean routesReady = new AtomicBoolean(false);

    public boolean shouldDeferLocationCollection() {
        return startupRunning.get() && !routesReady.get();
    }

    public boolean shouldDeferArrivalCollection() {
        return startupRunning.get();
    }

    public void markStartupStarted() {
        startupRunning.set(true);
        routesReady.set(false);
    }

    public void markRoutesReady() {
        routesReady.set(true);
    }

    public void markStartupFinished() {
        startupRunning.set(false);
    }
}
