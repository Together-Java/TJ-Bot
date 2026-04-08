package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import org.togetherjava.tjbot.features.projects.ProjectsNumericScoreListener;

import java.util.List;
import java.util.Objects;

/**
 * Configuration for the numeric score feature on forum posts, see
 * {@link ProjectsNumericScoreListener}.
 *
 * @param forumId the ID of the Discord forum channel to apply the score system to
 * @param upVoteEmoteName the name of the emoji used for upvoting (custom emoji name or raw unicode)
 * @param downVoteEmoteName the name of the emoji used for downvoting (custom emoji name or raw
 *        unicode)
 * @param zeroScore the emoji to display when the score is zero
 * @param positiveScores the emojis to display for positive scores, ordered from +1 upwards
 * @param negativeScores the emojis to display for negative scores, ordered from -1 downwards
 */
@JsonRootName("numericScoreConfig")
public record NumericScoreConfig(@JsonProperty(value = "forumId", required = true) long forumId,
        @JsonProperty(value = "upVoteEmoteName", required = true) String upVoteEmoteName,
        @JsonProperty(value = "downVoteEmoteName", required = true) String downVoteEmoteName,
        @JsonProperty(value = "zeroScore", required = true) String zeroScore,
        @JsonProperty(value = "positiveScores", required = true) List<String> positiveScores,
        @JsonProperty(value = "negativeScores", required = true) List<String> negativeScores) {

    /**
     * Creates a NumericScoreConfig and validates its fields.
     */
    public NumericScoreConfig {
        Objects.requireNonNull(upVoteEmoteName);
        Objects.requireNonNull(downVoteEmoteName);
        Objects.requireNonNull(zeroScore);
        positiveScores = List.copyOf(Objects.requireNonNull(positiveScores));
        negativeScores = List.copyOf(Objects.requireNonNull(negativeScores));
        if (positiveScores.isEmpty()) {
            throw new IllegalArgumentException("positiveScores must not be empty");
        }
    }
}
