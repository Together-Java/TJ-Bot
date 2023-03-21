package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.List;

@JsonRootName("starboard")
public final class StarboardConfig {
    private final List<String> emojiNames;
    private final String channelName;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public StarboardConfig(
            @JsonProperty(value = "emojiNames", required = true) List<String> emojiNames,
            @JsonProperty(value = "channelName", required = true) String channelName) {
        this.emojiNames = emojiNames;
        this.channelName = channelName;
    }

    /**
     * Gets the list of emotes that are recognized by the starboard feature. A message that is
     * reacted on with an emote in this list will be reposted in a special channel.
     * 
     * Empty to deactivate the feature.
     *
     * @return The List of emojis recognized by the starboard
     */
    public List<String> getEmojiNames() {
        return emojiNames;
    }

    /**
     * Gets the name of the channel with the starboard Deactivate by using a non-existent channel
     * name
     * 
     * @return the name of the channel with the starboard
     */

    public String getChannelName() {
        return channelName;
    }
}
