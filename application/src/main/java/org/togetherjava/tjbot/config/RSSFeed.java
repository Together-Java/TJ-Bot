package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record RSSFeed(@JsonProperty(value = "url", required = true) String url,
        @JsonProperty(value = "targetChannelPattern",
                required = true) String targetChannelPattern) {

    public RSSFeed {
        Objects.requireNonNull(url);
        Objects.requireNonNull(targetChannelPattern);
    }
}
