package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Starboard Config
 *
 * @param emojiNames the List of emojis which are recognized by the starboard
 * @param channelPattern the pattern of the channel with the starboard
 */
@JsonRootName("starboard")
public record StarboardConfig(List<String> emojiNames, Pattern channelPattern) {
    /**
     * Creates a Starboard config.
     *
     * @param emojiNames the List of emojis which are recognized by the starboard
     * @param channelPattern the pattern of the channel with the starboard
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public StarboardConfig {
        Objects.requireNonNull(emojiNames);
        Objects.requireNonNull(channelPattern);
    }

    /**
     * Gets the list of emotes that are recognized by the starboard feature. A message that is
     * reacted on with an emote in this list will be reposted in a special channel
     * {@link #channelPattern()}.
     * <p>
     * Empty to deactivate the feature.
     *
     * @return The List of emojis recognized by the starboard
     */
    @Override
    public List<String> emojiNames() {
        return emojiNames;
    }

    /**
     * Gets the pattern of the channel with the starboard. Deactivate by using a non-existent
     * channel name.
     *
     * @return the pattern of the channel with the starboard
     */
    public Pattern channelPattern() {
        return channelPattern;
    }
}
