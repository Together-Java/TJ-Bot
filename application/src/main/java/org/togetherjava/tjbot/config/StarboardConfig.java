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
    public StarboardConfig(@JsonProperty(value = "emojiNames", required = true) List<String> emojiNames,
                           @JsonProperty(value = "channelName", required = true) String starboardChannelName) {
        this.emojiNames = emojiNames;
        this.starboardChannelName = starboardChannelName;
    }

    /**
     * Gets the names of the emojis whose users react with for it to be put on the starboard
     *
     * @return The names of the emojis whose users react with for it to be put on the starboard
     * */
    public List<String> getEmojiNames() {
        return emojiNames;
    }
    /**
     * Gets the name of the channel with the starboard
     *
     * @return the name of the channel with the starboard
     * */

    public String getStarboardChannelName() {
        return starboardChannelName;
    }
}
