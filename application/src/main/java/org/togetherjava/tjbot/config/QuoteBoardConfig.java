package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.apache.logging.log4j.LogManager;

import org.togetherjava.tjbot.features.basic.QuoteBoardForwarder;

import java.util.Objects;

/**
 * Configuration for the quote board feature, see {@link QuoteBoardForwarder}.
 */
@JsonRootName("quoteBoardConfig")
public record QuoteBoardConfig(
        @JsonProperty(value = "minimumReactionsToTrigger", required = true) int minimumReactions,
        @JsonProperty(required = true) String channel,
        @JsonProperty(value = "reactionEmoji", required = true) String reactionEmoji) {

    /**
     * Creates a QuoteBoardConfig.
     *
     * @param minimumReactions the minimum amount of reactions
     * @param channel the pattern for the board channel
     * @param reactionEmoji the emoji with which users should react to
     */
    public QuoteBoardConfig {
        if (minimumReactions <= 0) {
            throw new IllegalArgumentException("minimumReactions must be greater than zero");
        }
        Objects.requireNonNull(channel);
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be empty or blank");
        }
        Objects.requireNonNull(reactionEmoji);
        if (reactionEmoji.isBlank()) {
            throw new IllegalArgumentException("reactionEmoji must not be empty or blank");
        }
        LogManager.getLogger(QuoteBoardConfig.class)
            .debug("Quote-Board configs loaded: minimumReactions={}, channel='{}', reactionEmoji='{}'",
                    minimumReactions, channel, reactionEmoji);
    }
}
