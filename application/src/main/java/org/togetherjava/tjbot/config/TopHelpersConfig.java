package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.Objects;

/**
 * Configuration for the top helper system, see
 * {@link org.togetherjava.tjbot.features.tophelper.TopHelpersCommand}.
 */
@JsonRootName("topHelpers")
public final class TopHelpersConfig {
    private final String rolePattern;
    private final String assignmentChannelPattern;
    private final String announcementChannelPattern;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private TopHelpersConfig(
            @JsonProperty(value = "rolePattern", required = true) String rolePattern,
            @JsonProperty(value = "assignmentChannelPattern",
                    required = true) String assignmentChannelPattern,
            @JsonProperty(value = "announcementChannelPattern",
                    required = true) String announcementChannelPattern) {
        this.rolePattern = Objects.requireNonNull(rolePattern);
        this.assignmentChannelPattern = Objects.requireNonNull(assignmentChannelPattern);
        this.announcementChannelPattern = Objects.requireNonNull(announcementChannelPattern);
    }

    /**
     * Gets the REGEX pattern matching the role used to represent Top Helpers.
     *
     * @return the role name pattern
     */
    public String getRolePattern() {
        return rolePattern;
    }

    /**
     * Gets the REGEX pattern used to identify the channel where Top Helper assignments are
     * automatically executed.
     *
     * @return the channel name pattern
     */
    public String getAssignmentChannelPattern() {
        return assignmentChannelPattern;
    }

    /**
     * Gets the REGEX pattern used to identify the channel where Top Helper announcements are send.
     *
     * @return the channel name pattern
     */
    public String getAnnouncementChannelPattern() {
        return announcementChannelPattern;
    }
}
