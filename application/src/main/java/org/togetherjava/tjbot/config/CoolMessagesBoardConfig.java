package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

/**
 * Configuration for the cool messages board feature, see
 * {@link org.togetherjava.tjbot.features.basic.CoolMessagesBoardManager}.
 */
@JsonRootName("coolMessagesConfig")
public final class CoolMessagesBoardConfig {
    private final String boardChannelPattern;
    private final int minimumReactions;

    private CoolMessagesBoardConfig(
            @JsonProperty(value = "minimumReactions", required = true) int minimumReactions,
            @JsonProperty(value = "boardChannelPattern",
                    required = true) String boardChannelPattern) {
        this.minimumReactions = minimumReactions;
        this.boardChannelPattern = boardChannelPattern;
    }

    /**
     * Gets the minimum amount of reactions needed for a message to be considered as a quote.
     *
     * @return the minimum amount of reactions
     */
    public int getMinimumReactions() {
        return minimumReactions;
    }

    /**
     * Gets the REGEX pattern used to identify the quotes text channel
     *
     * @return the channel name pattern
     */
    public String getBoardChannelPattern() {
        return boardChannelPattern;
    }
}
