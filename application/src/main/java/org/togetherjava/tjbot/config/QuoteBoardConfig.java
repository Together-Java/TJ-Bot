package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.apache.logging.log4j.LogManager;

import org.togetherjava.tjbot.features.basic.QuoteBoardForwarder;

import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the quote board feature, see {@link QuoteBoardForwarder}.
 */
@JsonRootName("quoteBoardConfig")
public record QuoteBoardConfig(
        @JsonProperty(value = "minimumScoreToTrigger", required = true) float minimumScoreToTrigger,
        @JsonProperty(value = "channel", required = true) String channel,
        @JsonProperty(value = "botEmoji", required = true) String botEmoji,
        @JsonProperty(value = "defaultEmojiScore", required = true) float defaultEmojiScore,
        @JsonProperty(value = "emojiScores", required = true) Map<String, Float> emojiScores) {

    /**
     * Creates a QuoteBoardConfig.
     *
     * @param minimumScoreToTrigger the minimum amount of reaction score for a message to be quoted
     * @param channel the pattern for the board channel
     * @param botEmoji the emoji with which the bot will mark quoted messages
     * @param defaultEmojiScore the default score of an emoji if it's not in the emojiScores map
     * @param emojiScores a map of each emoji's custom score
     */
    public QuoteBoardConfig {
        if (minimumScoreToTrigger <= 0) {
            throw new IllegalArgumentException("minimumScoreToTrigger must be greater than zero");
        }
        Objects.requireNonNull(channel);
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be empty or blank");
        }
        Objects.requireNonNull(botEmoji);
        if (botEmoji.isBlank()) {
            throw new IllegalArgumentException("reactionEmoji must not be empty or blank");
        }
        Objects.requireNonNull(emojiScores);
        LogManager.getLogger(QuoteBoardConfig.class)
            .debug("Quote-Board configs loaded: minimumReactions={}, channel='{}', reactionEmoji='{}'",
                    minimumScoreToTrigger, channel, botEmoji);
    }
}
