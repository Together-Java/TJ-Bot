package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public record ApplicationFormConfig(
        @JsonProperty(value = "roles", required = true) List<ApplyRoleConfig> applyRoleConfig,
        @JsonProperty(value = "applicationChannelPattern",
                required = true) String applicationChannelPattern) {

    public ApplicationFormConfig {
        Objects.requireNonNull(applyRoleConfig);
        Objects.requireNonNull(applicationChannelPattern);
    }
}
