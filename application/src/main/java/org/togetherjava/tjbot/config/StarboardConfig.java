package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.List;

@JsonRootName("starboard")
public final class StarboardConfig {
    private final List<String> emojiNames;
    private final String starboardChannelName;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public StarboardConfig(@JsonProperty(value = "starboardEmojiNames", required = true) List<String> emojiNames,
                           @JsonProperty(value = "starboardChannelName", required = true) String starboardChannelName) {
        this.emojiNames = emojiNames;
        this.starboardChannelName = starboardChannelName;
    }

    public List<String> getEmojiNames() {
        return emojiNames;
    }

    public String getStarboardChannelName() {
        return starboardChannelName;
    }
}
