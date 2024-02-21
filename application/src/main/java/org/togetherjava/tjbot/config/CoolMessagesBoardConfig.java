package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.Objects;

/**
 * Configuration for the cool messages board feature, see
 * {@link org.togetherjava.tjbot.features.basic.CoolMessagesBoardManager}.
 */
@JsonRootName("coolMessagesConfig")
public record CoolMessagesBoardConfig(
        @JsonProperty(value = "minimumReactions", required = true) int minimumReactions,
        @JsonProperty(value = "boardChannelPattern", required = true) String boardChannelPattern) {

    /**
     * Creates a CoolMessagesBoardConfig.
     *
     * @param minimumReactions the minimum amount of reactions
     * @param boardChannelPattern the pattern for the board channel
     */
    public CoolMessagesBoardConfig {
        Objects.requireNonNull(boardChannelPattern);
    }
}
