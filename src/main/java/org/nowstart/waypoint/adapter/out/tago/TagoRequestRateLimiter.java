package org.nowstart.waypoint.adapter.out.tago;

import org.nowstart.waypoint.config.TagoCollectionProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TagoRequestRateLimiter {

    private final TagoCollectionProperties properties;
    private final AtomicLong nextPermitNanos = new AtomicLong(System.nanoTime());

    public TagoRequestRateLimiter(TagoCollectionProperties properties) {
        this.properties = properties;
    }

    void acquire() {
        long intervalNanos = permitIntervalNanos(properties.rateLimit());
        while (true) {
            long now = System.nanoTime();
            long current = nextPermitNanos.get();
            long permitAt = Math.max(now, current);
            long next = permitAt + intervalNanos;
            if (nextPermitNanos.compareAndSet(current, next)) {
                sleepUntilPermit(permitAt - now);
                return;
            }
        }
    }

    static long permitIntervalNanos(int rateLimit) {
        long oneSecondNanos = TimeUnit.SECONDS.toNanos(1);
        return Math.max(1L, (oneSecondNanos + rateLimit - 1L) / rateLimit);
    }

    private static void sleepUntilPermit(long waitNanos) {
        if (waitNanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for TAGO API rate limit permit.", ex);
        }
    }
}
