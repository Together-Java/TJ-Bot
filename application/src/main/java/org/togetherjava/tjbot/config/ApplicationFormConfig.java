package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents the configuration for an application form, including roles and application channel
 * pattern.
 */
public record ApplicationFormConfig(
        @JsonProperty(value = "roles", required = true) List<ApplyRoleConfig> applyRoleConfig,
        @JsonProperty(value = "applicationChannelPattern",
                required = true) String applicationChannelPattern) {

    /**
     * Constructs an instance of {@link ApplicationFormConfig} with the provided parameters.
     *
     * @param applyRoleConfig the list of ApplyRoleConfig objects defining roles for the application
     *        form
     * @param applicationChannelPattern the pattern used to identify the application channel
     */
    public ApplicationFormConfig {
        Objects.requireNonNull(applyRoleConfig);
        Objects.requireNonNull(applicationChannelPattern);
    }
}
