package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents the configuration for an application form, including roles and application channel
 * pattern.
 */
public record RoleApplicationSystemConfig(
        @JsonProperty(value = "submissionsChannelPattern",
                required = true) String submissionsChannelPattern,
        @JsonProperty(value = "defaultQuestion", required = true) String defaultQuestion,
        @JsonProperty(value = "minimumAnswerLength", required = true) int minimumAnswerLength,
        @JsonProperty(value = "maximumAnswerLength", required = true) int maximumAnswerLength,
        @JsonProperty(value = "applicationSubmitCooldownMinutes",
                required = true) int applicationSubmitCooldownMinutes) {

    /**
     * Constructs an instance of {@link RoleApplicationSystemConfig} with the provided parameters.
     *
     * @param submissionsChannelPattern the pattern used to identify the application channel
     * @param defaultQuestion the default question for the form
     */
    public RoleApplicationSystemConfig {
        Objects.requireNonNull(submissionsChannelPattern);
        Objects.requireNonNull(defaultQuestion);
    }
}
