package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CakeDayConfig(
        @JsonProperty(value = "rolePattern", required = true) String rolePattern) {
}
