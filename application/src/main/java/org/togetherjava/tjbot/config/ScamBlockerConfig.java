package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for the scam blocker system, see
 * {@link org.togetherjava.tjbot.features.moderation.scam.ScamBlocker}.
 */
@JsonRootName("scamBlocker")
public final class ScamBlockerConfig {
    private final Mode mode;
    private final String reportChannelPattern;
    private final String botTrapChannelPattern;
    private final Set<String> suspiciousKeywords;
    private final Set<String> hostWhitelist;
    private final Set<String> hostBlacklist;
    private final Set<String> suspiciousHostKeywords;
    private final int isHostSimilarToKeywordDistanceThreshold;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private ScamBlockerConfig(@JsonProperty(value = "mode", required = true) Mode mode,
            @JsonProperty(value = "reportChannelPattern",
                    required = true) String reportChannelPattern,
            @JsonProperty(value = "botTrapChannelPattern",
                    required = true) String botTrapChannelPattern,
            @JsonProperty(value = "suspiciousKeywords",
                    required = true) Set<String> suspiciousKeywords,
            @JsonProperty(value = "hostWhitelist", required = true) Set<String> hostWhitelist,
            @JsonProperty(value = "hostBlacklist", required = true) Set<String> hostBlacklist,
            @JsonProperty(value = "suspiciousHostKeywords",
                    required = true) Set<String> suspiciousHostKeywords,
            @JsonProperty(value = "isHostSimilarToKeywordDistanceThreshold",
                    required = true) int isHostSimilarToKeywordDistanceThreshold) {
        this.mode = Objects.requireNonNull(mode);
        this.reportChannelPattern = Objects.requireNonNull(reportChannelPattern);
        this.botTrapChannelPattern = Objects.requireNonNull(botTrapChannelPattern);
        this.suspiciousKeywords = new HashSet<>(Objects.requireNonNull(suspiciousKeywords));
        this.hostWhitelist = new HashSet<>(Objects.requireNonNull(hostWhitelist));
        this.hostBlacklist = new HashSet<>(Objects.requireNonNull(hostBlacklist));
        this.suspiciousHostKeywords = new HashSet<>(Objects.requireNonNull(suspiciousHostKeywords));
        this.isHostSimilarToKeywordDistanceThreshold = isHostSimilarToKeywordDistanceThreshold;
    }

    /**
     * Gets the mode of the scam blocker. Controls which actions it takes when detecting scam.
     *
     * @return the scam blockers mode
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Gets the REGEX pattern used to identify the channel that is used to report identified scam
     * to.
     *
     * @return the channel name pattern
     */
    public String getReportChannelPattern() {
        return reportChannelPattern;
    }

    /**
     * Gets the REGEX pattern used to identify the channel that is used to as bot-trap. Sending
     * messages in this channel identifies the author as bot.
     *
     * @return the channel name pattern
     */
    public String getBotTrapChannelPattern() {
        return botTrapChannelPattern;
    }

    /**
     * Gets the set of keywords that are considered suspicious if they appear in a message.
     *
     * @return the set of suspicious keywords
     */
    public Set<String> getSuspiciousKeywords() {
        return Collections.unmodifiableSet(suspiciousKeywords);
    }

    /**
     * Gets the set of trusted hosts. Urls using those hosts are not considered scam.
     *
     * @return the whitelist of hosts
     */
    public Set<String> getHostWhitelist() {
        return Collections.unmodifiableSet(hostWhitelist);
    }

    /**
     * Gets the set of known scam hosts. Urls using those hosts are considered scam.
     *
     * @return the blacklist of hosts
     */
    public Set<String> getHostBlacklist() {
        return Collections.unmodifiableSet(hostBlacklist);
    }

    /**
     * Gets the set of keywords that are considered suspicious if they appear in host names. Urls
     * using hosts that have those, or similar, keywords in their name, are considered suspicious.
     *
     * @return the set of suspicious host keywords
     */
    public Set<String> getSuspiciousHostKeywords() {
        return Collections.unmodifiableSet(suspiciousHostKeywords);
    }

    /**
     * Gets the threshold used to determine whether a host is similar to a given keyword. If the
     * host contains an infix with an edit distance that is below this threshold, they are
     * considered similar.
     *
     * @return the threshold to determine similarity
     */
    public int getIsHostSimilarToKeywordDistanceThreshold() {
        return isHostSimilarToKeywordDistanceThreshold;
    }

    /**
     * Mode of a scam blocker. Controls which actions it takes when detecting scam.
     */
    public enum Mode {
        /**
         * The blocker is turned off and will not scan any messages for scam.
         */
        OFF,
        /**
         * The blocker will log any detected scam but will not take action on them.
         */
        ONLY_LOG,
        /**
         * Detected scam will be sent to moderators for review. Any action has to be approved
         * explicitly first.
         */
        APPROVE_FIRST,
        /**
         * Detected scam will automatically be deleted. A moderator will be informed for review.
         * They can then decide whether the user should be put into quarantine.
         */
        AUTO_DELETE_BUT_APPROVE_QUARANTINE,
        /**
         * The blocker will automatically delete any detected scam and put the user into quarantine.
         */
        AUTO_DELETE_AND_QUARANTINE
    }
}
