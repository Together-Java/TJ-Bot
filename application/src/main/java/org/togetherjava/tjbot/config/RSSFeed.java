package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import java.util.Objects;

public record RSSFeed(@JsonProperty(value = "url", required = true) String url,
        @JsonProperty(value = "targetChannelPattern") @Nullable String targetChannelPattern,
        @JsonProperty(value = "dateFormatterPattern",
                required = true) String dateFormatterPattern) {

    public RSSFeed {
        Objects.requireNonNull(url);
        Objects.requireNonNull(targetChannelPattern);
        Objects.requireNonNull(dateFormatterPattern);
    }
}
