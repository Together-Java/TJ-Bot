package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record ApplyRoleConfig(@JsonProperty(value = "name", required = true) String name,
        @JsonProperty(value = "description", required = true) String description,
        @JsonProperty(value = "formattedEmoji") String emoji) {

    public ApplyRoleConfig {
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
    }
}
