package org.togetherjava.tjbot.config;

import com.linkedin.urls.Url;

import org.togetherjava.tjbot.features.utils.RateLimiter;

import java.net.MalformedURLException;

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
     * @param baseUrl the base url of the JShell REST API, must be valid
     * @param rateLimitWindowSeconds the number of seconds of the {@link RateLimiter rate limiter}
     *        for jshell commands and code actions, must be higher than 0
     * @param rateLimitRequestsInWindow the number of requests of the {@link RateLimiter rate
     *        limiter} for jshell commands and code actions, must be higher than 0
     */
    public JShellConfig {
        try {
            Url.create(baseUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
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
