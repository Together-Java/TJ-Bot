package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents the configuration for an RSS feed, which includes the list of feeds to subscribe to, a
 * pattern for identifying Java news channels, and the interval (in minutes) for polling the feeds.
 *
 * @param feeds The list of RSS feeds to subscribe to.
 * @param fallbackChannelPattern The pattern used to identify the fallback text channel to use.
 * @param pollIntervalInMinutes The interval (in minutes) for polling the RSS feeds for updates.
 */
public record RSSFeedsConfig(@JsonProperty(value = "feeds", required = true) List<RSSFeed> feeds,
        @JsonProperty(value = "fallbackChannelPattern",
                required = true) String fallbackChannelPattern,
        @JsonProperty(value = "pollIntervalInMinutes", required = true) int pollIntervalInMinutes) {

    /**
     * Constructs a new {@link RSSFeedsConfig}.
     *
     * @param feeds The list of RSS feeds to subscribe to.
     * @param fallbackChannelPattern The pattern used to identify the fallback text channel to use.
     * @param pollIntervalInMinutes The interval (in minutes) for polling the RSS feeds for updates.
     * @throws NullPointerException if any of the parameters (feeds or fallbackChannelPattern) are
     *         null
     */
    public RSSFeedsConfig {
        Objects.requireNonNull(feeds);
        Objects.requireNonNull(fallbackChannelPattern);
    }
}
