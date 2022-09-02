package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the help system, see {@link org.togetherjava.tjbot.features.help.AskCommand}.
 */
@SuppressWarnings("ClassCanBeRecord")
@JsonRootName("helpSystem")
public final class HelpSystemConfig {
    private final String stagingChannelPattern;
    private final String overviewChannelPattern;
    private final List<String> categories;
    private final String categoryRoleSuffix;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private HelpSystemConfig(@JsonProperty("stagingChannelPattern") String stagingChannelPattern,
            @JsonProperty("overviewChannelPattern") String overviewChannelPattern,
            @JsonProperty("categories") List<String> categories,
            @JsonProperty("categoryRoleSuffix") String categoryRoleSuffix) {
        this.stagingChannelPattern = stagingChannelPattern;
        this.overviewChannelPattern = overviewChannelPattern;
        this.categories = new ArrayList<>(categories);
        this.categoryRoleSuffix = categoryRoleSuffix;
    }

    /**
     * Gets the REGEX pattern used to identify the channel that acts as the staging channel for
     * getting help. Users ask help here and help threads are also created in this channel.
     *
     * @return the channel name pattern
     */
    public String getStagingChannelPattern() {
        return stagingChannelPattern;
    }

    /**
     * Gets the REGEX pattern used to identify the channel that provides an overview of all active
     * help threads.
     *
     * @return the channel name pattern
     */
    public String getOverviewChannelPattern() {
        return overviewChannelPattern;
    }

    /**
     * Gets a list of all categories, available to categorize help questions.
     *
     * @return a list of all categories
     */
    public List<String> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    /**
     * Gets the suffix that, together with a category from {@link #getCategories()}, forms the name
     * of the role of people interested in helping with questions that are categorized with the
     * corresponding category.
     *
     * E.g. if the category is {@code "Java"} and the suffix {@code "- Helper"}, the name of the
     * role is {@code "Java - Helper"}.
     *
     * @return the suffix
     */
    public String getCategoryRoleSuffix() {
        return categoryRoleSuffix;
    }
}
