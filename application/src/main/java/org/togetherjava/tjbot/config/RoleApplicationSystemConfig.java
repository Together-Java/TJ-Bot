package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.interactions.components.text.TextInput;

import java.util.Objects;

/**
 * Represents the configuration for an application form, including roles and application channel
 * pattern.
 *
 * @param submissionsChannelPattern the pattern used to identify the submissions channel where applications are sent
 * @param defaultQuestion the default question that will be asked in the role application form
 * @param minimumAnswerLength the minimum number of characters required for the applicant's answer
 * @param maximumAnswerLength the maximum number of characters allowed for the applicant's answer
 * @param applicationSubmitCooldownMinutes the cooldown time in minutes before the user can submit another application
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
     * <p>
     * This constructor ensures that {@code submissionsChannelPattern} and {@code defaultQuestion}
     * are not null and that the length of the {@code defaultQuestion} does not exceed the maximum allowed length.
     */
    public RoleApplicationSystemConfig {
        Objects.requireNonNull(submissionsChannelPattern);
        Objects.requireNonNull(defaultQuestion);

        if (defaultQuestion.length() > TextInput.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("defaultQuestion length is too long! Cannot be greater than %d".formatted(TextInput.MAX_LABEL_LENGTH));
        }
    }
}
