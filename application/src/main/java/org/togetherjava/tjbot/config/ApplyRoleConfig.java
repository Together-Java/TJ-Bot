package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents the configuration for applying a role.
 */
public record ApplyRoleConfig(@JsonProperty(value = "name", required = true) String name,
        @JsonProperty(value = "description", required = true) String description,
        @JsonProperty(value = "formattedEmoji") String emoji) {

    /**
     * Constructs an instance of ApplyRoleConfig with the given parameters.
     *
     * @param name the name of the role
     * @param description the description of the role
     * @param emoji the emoji associated with the role
     */
    public ApplyRoleConfig {
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
    }
}
