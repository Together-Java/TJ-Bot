package org.togetherjava.tjbot.config;

import com.linkedin.urls.Url;

import java.net.MalformedURLException;

public record JShellConfig(String baseUrl, int rateLimitWindowSeconds,
        int rateLimitRequestsInWindow) {
    public JShellConfig {
        try {
            Url.create(baseUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
        if (rateLimitWindowSeconds <= 0)
            throw new IllegalArgumentException(
                    "Illegal rateLimitWindowSeconds : " + rateLimitWindowSeconds);
        if (rateLimitRequestsInWindow <= 0)
            throw new IllegalArgumentException(
                    "Illegal rateLimitRequestsInWindow : " + rateLimitRequestsInWindow);
    }
}
