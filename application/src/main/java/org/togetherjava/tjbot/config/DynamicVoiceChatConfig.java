package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Configuration for the dynamic voice chat feature.
 *
 * @param archiveCategoryPattern the name of the Discord Guild category in which the archived
 *        channels will go
 * @param cleanChannelsAmount the amount of channels to clean once a cleanup is triggered
 * @param minimumChannelsAmount the amount of voice channels for the archive category to have before
 *        a cleanup triggers
 */
public record DynamicVoiceChatConfig(
        @JsonProperty(value = "dynamicChannelPatterns",
                required = true) List<Pattern> dynamicChannelPatterns,
        @JsonProperty(value = "archiveCategoryPattern",
                required = true) String archiveCategoryPattern,
        @JsonProperty(value = "cleanChannelsAmount") int cleanChannelsAmount,
        @JsonProperty(value = "minimumChannelsAmount", required = true) int minimumChannelsAmount) {

    /**
     * Constructs an instance of {@code DynamicVoiceChatConfig} and throws if
     * {@code dynamicChannelPatterns} or @{code archiveCategoryPattern} is null.
     */
    public DynamicVoiceChatConfig {
        Objects.requireNonNull(dynamicChannelPatterns);
        Objects.requireNonNull(archiveCategoryPattern);
    }
}
