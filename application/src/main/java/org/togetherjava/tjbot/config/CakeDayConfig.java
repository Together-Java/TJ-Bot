package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration record for the Cake Day feature.
 */
public record CakeDayConfig(
        @JsonProperty(value = "rolePattern", required = true) String rolePattern) {
}
