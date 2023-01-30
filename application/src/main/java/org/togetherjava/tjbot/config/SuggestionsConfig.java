package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.Objects;

/**
 * Configuration for the suggestion system, see
 * {@link org.togetherjava.tjbot.features.basic.SuggestionsUpDownVoter}.
 */
@JsonRootName("suggestions")
public final class SuggestionsConfig {
    private final String channelPattern;
    private final String upVoteEmoteName;
    private final String downVoteEmoteName;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private SuggestionsConfig(
            @JsonProperty(value = "channelPattern", required = true) String channelPattern,
            @JsonProperty(value = "upVoteEmoteName", required = true) String upVoteEmoteName,
            @JsonProperty(value = "downVoteEmoteName", required = true) String downVoteEmoteName) {
        this.channelPattern = Objects.requireNonNull(channelPattern);
        this.upVoteEmoteName = Objects.requireNonNull(upVoteEmoteName);
        this.downVoteEmoteName = Objects.requireNonNull(downVoteEmoteName);
    }

    /**
     * Gets the REGEX pattern used to identify channels that are used for sending suggestions.
     *
     * @return the channel name pattern
     */
    public String getChannelPattern() {
        return channelPattern;
    }

    /**
     * Gets the name of the emote used to up-vote suggestions.
     *
     * @return the name of the up-vote emote
     */
    public String getUpVoteEmoteName() {
        return upVoteEmoteName;
    }

    /**
     * Gets the name of the emote used to down-vote suggestions.
     *
     * @return the name of the down-vote emote
     */
    public String getDownVoteEmoteName() {
        return downVoteEmoteName;
    }
}
