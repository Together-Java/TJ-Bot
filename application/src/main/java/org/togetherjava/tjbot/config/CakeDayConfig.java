package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Configuration record for the Cake Day feature.
 */
public record CakeDayConfig(
        @JsonProperty(value = "rolePattern", required = true) String rolePattern) {

    /**
     * Configuration constructor for the Cake Day feature.
     */
    public CakeDayConfig {
        Objects.requireNonNull(rolePattern);
    }
}
