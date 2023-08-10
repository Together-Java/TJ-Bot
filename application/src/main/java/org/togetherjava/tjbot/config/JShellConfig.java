package org.togetherjava.tjbot.config;


import org.togetherjava.tjbot.features.utils.RateLimiter;

import java.util.Objects;

/**
 * JShell config.
 * 
 * @param baseUrl the base url of the JShell REST API
 * @param rateLimitWindowSeconds the number of seconds of the {@link RateLimiter rate limiter} for
 *        jshell commands and code actions
 * @param rateLimitRequestsInWindow the number of requests of the {@link RateLimiter rate limiter}
 *        for jshell commands and code actions
 */
public record JShellConfig(String baseUrl, int rateLimitWindowSeconds,
        int rateLimitRequestsInWindow) {
    /**
     * Creates a JShell config.
     * 
     * @param baseUrl the base url of the JShell REST API, must be not null
     * @param rateLimitWindowSeconds the number of seconds of the {@link RateLimiter rate limiter}
     *        for jshell commands and code actions, must be higher than 0
     * @param rateLimitRequestsInWindow the number of requests of the {@link RateLimiter rate
     *        limiter} for jshell commands and code actions, must be higher than 0
     */
    public JShellConfig {
        Objects.requireNonNull(baseUrl);
        if (rateLimitWindowSeconds < 0) {
            throw new IllegalArgumentException(
                    "Illegal rateLimitWindowSeconds : " + rateLimitWindowSeconds);
        }
        if (rateLimitRequestsInWindow < 0) {
            throw new IllegalArgumentException(
                    "Illegal rateLimitRequestsInWindow : " + rateLimitRequestsInWindow);
        }
    }
}
