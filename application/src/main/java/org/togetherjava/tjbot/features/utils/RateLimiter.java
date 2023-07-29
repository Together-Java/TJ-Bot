package org.togetherjava.tjbot.features.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom rate limiter.
 */
public class RateLimiter {

    private List<Instant> lastUses;

    private final Duration duration;
    private final int allowedRequests;

    public RateLimiter(Duration duration, int allowedRequests) {
        this.duration = duration;
        this.allowedRequests = allowedRequests;

        this.lastUses = List.of();
    }

    public boolean allowRequest(Instant time) {
        synchronized (this) {
            List<Instant> usesInWindow = getEffectiveUses(time);

            if (usesInWindow.size() >= allowedRequests) {
                return false;
            }
            usesInWindow.add(time);

            lastUses = usesInWindow;

            return true;
        }
    }

    private List<Instant> getEffectiveUses(Instant time) {
        return lastUses.stream()
            .filter(it -> Duration.between(it, time).compareTo(duration) <= 0)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public Instant nextAllowedRequestTime(Instant time) {
        synchronized (this) {
            List<Instant> currentUses = getEffectiveUses(time);
            currentUses.sort(Instant::compareTo);

            if (currentUses.size() < allowedRequests) {
                return Instant.now();
            }

            return currentUses.get(0).plus(duration);
        }
    }

}
