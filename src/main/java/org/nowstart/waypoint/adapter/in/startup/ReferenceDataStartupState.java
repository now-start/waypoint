package org.nowstart.waypoint.adapter.in.startup;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ReferenceDataStartupState {

    private final AtomicBoolean running = new AtomicBoolean(true);

    public boolean isRunning() {
        return running.get();
    }

    public void markStarted() {
        running.set(true);
    }

    public void markFinished() {
        running.set(false);
    }
}
