package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents the configuration for an RSS feed, which includes the list of feeds to subscribe to, a
 * pattern for identifying Java news channels, and the interval (in minutes) for polling the feeds.
 */
public record RSSFeedsConfig(@JsonProperty(value = "feeds", required = true) List<RSSFeed> feeds,
        @JsonProperty(value = "javaNewsChannelPattern",
                required = true) String javaNewsChannelPattern,
        @JsonProperty(value = "rssPollInterval", required = true) int rssPollInterval) {

    /**
     * Constructs a new {@link RSSFeedsConfig}.
     *
     * @param feeds The list of RSS feeds to subscribe to.
     * @param javaNewsChannelPattern The pattern used to identify Java news channels within the RSS
     *        feeds.
     * @param rssPollInterval The interval (in minutes) for polling the RSS feeds for updates.
     * @throws NullPointerException if any of the parameters (feeds or javaNewsChannelPattern) are
     *         null
     */
    public RSSFeedsConfig {
        Objects.requireNonNull(feeds);
        Objects.requireNonNull(javaNewsChannelPattern);
    }
}
