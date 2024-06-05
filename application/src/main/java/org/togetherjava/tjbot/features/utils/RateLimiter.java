package org.togetherjava.tjbot.features.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rate limiter, register when requests are done and tells if a request can be done or need to be
 * canceled.
 */
public class RateLimiter {

    private List<Instant> lastUses;

    private final Duration duration;
    private final int allowedRequests;

    /**
     * Creates a rate limiter.
     * <p>
     * Defines a window and a number of request, for example, if 10 requests should be allowed per 5
     * seconds, so 10/5s, the following should be called:
     * {@snippet java: new RateLimit(Duration.of(5, TimeUnit.SECONDS), 10) }
     * 
     * @param duration the duration of window
     * @param allowedRequests the number of requests to allow in the window
     */
    public RateLimiter(Duration duration, int allowedRequests) {
        this.duration = duration;
        this.allowedRequests = allowedRequests;

        this.lastUses = List.of();
    }

    /**
     * Tries to allow the request. If it is allowed, the time is registered.
     * 
     * @param time the time of the request
     * @return if the request was allowed
     */
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

    /**
     * Returns next time a request can be allowed.
     * 
     * @param time the time of the request
     * @return when the next request will be allowed
     */
    public Instant nextAllowedRequestTime(Instant time) {
        synchronized (this) {
            List<Instant> currentUses = getEffectiveUses(time);
            currentUses.sort(Instant::compareTo);

            if (currentUses.size() < allowedRequests) {
                return Instant.now();
            }

            return currentUses.getFirst().plus(duration);
        }
    }

}
