package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

/**
 * Configuration for the suggestion system, see
 * {@link org.togetherjava.tjbot.commands.basic.SuggestionsUpDownVoter}.
 */
@SuppressWarnings("ClassCanBeRecord")
@JsonRootName("suggestions")
public final class SuggestionsConfig {
    private final String channelPattern;
    private final String upVoteEmoteName;
    private final String downVoteEmoteName;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private SuggestionsConfig(@JsonProperty("channelPattern") String channelPattern,
            @JsonProperty("upVoteEmoteName") String upVoteEmoteName,
            @JsonProperty("downVoteEmoteName") String downVoteEmoteName) {
        this.channelPattern = channelPattern;
        this.upVoteEmoteName = upVoteEmoteName;
        this.downVoteEmoteName = downVoteEmoteName;
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
