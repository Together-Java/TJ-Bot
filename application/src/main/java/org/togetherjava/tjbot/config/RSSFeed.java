package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record RSSFeed(
        @JsonProperty(value = "url", required = true) String url) {

    public RSSFeed {
        Objects.requireNonNull(url);
    }
}
