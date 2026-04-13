package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Stream;

/**
 * Configuration of the numeric score feature.
 * 
 * @param forumId the forum where to apply the numeric score feature
 * @param upvoteEmoji the upvote emoji
 * @param downvoteEmoji the downvote emoji
 * @param zeroScoreEmoji the emoji for the score zero
 * @param positiveScoresEmojis the emojis for positive scores starting at 1 ascending
 * @param negativeScoresEmojis the emojis for negative scores starting at -1 descending
 * @param addedEmojiBlackList the blacklisted emojis on top of score emojis
 */
public record NumericScoreConfig(@JsonProperty(value = "forumId", required = true) long forumId,
        @JsonProperty(value = "upvoteEmoji", required = true) String upvoteEmoji,
        @JsonProperty(value = "downvoteEmoji", required = true) String downvoteEmoji,
        @JsonProperty(value = "zeroScoreEmoji", required = true) String zeroScoreEmoji,
        @JsonProperty(value = "positiveScoresEmojis",
                required = true) List<String> positiveScoresEmojis,
        @JsonProperty(value = "negativeScoresEmojis",
                required = true) List<String> negativeScoresEmojis,
        @JsonProperty(value = "addedEmojiBlackList",
                required = true) List<String> addedEmojiBlackList) {
    public Stream<String> streamAllBlacklistedEmojis() {
        return Stream.concat(
                Stream.concat(positiveScoresEmojis.stream(), negativeScoresEmojis.stream()),
                Stream.concat(Stream.of(zeroScoreEmoji), addedEmojiBlackList.stream()));
    }
}
