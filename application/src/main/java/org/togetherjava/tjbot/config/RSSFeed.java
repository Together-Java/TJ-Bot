package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Represents an RSS feed configuration.
 */
public record RSSFeed(@JsonProperty(value = "url", required = true) String url,
        @JsonProperty(value = "targetChannelPattern") @Nullable String targetChannelPattern,
        @JsonProperty(value = "dateFormatterPattern",
                required = true) String dateFormatterPattern) {

    /**
     * Constructs an RSSFeed object.
     *
     * @param url the URL of the RSS feed
     * @param targetChannelPattern the target channel pattern
     * @param dateFormatterPattern the date formatter pattern
     * @throws NullPointerException if any of the parameters are null
     */
    public RSSFeed {
        Objects.requireNonNull(url);
        Objects.requireNonNull(targetChannelPattern);
        Objects.requireNonNull(dateFormatterPattern);
    }
}
