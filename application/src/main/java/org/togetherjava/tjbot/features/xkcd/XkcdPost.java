package org.togetherjava.tjbot.features.xkcd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record XkcdPost(int id, String safeTitle, String transcript, String alt, String img,
        String title) {
}
