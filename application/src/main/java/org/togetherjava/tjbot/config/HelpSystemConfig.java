package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for the help system, see {@link org.togetherjava.tjbot.commands.help.AskCommand}.
 */
@JsonRootName("helpSystem")
public final class HelpSystemConfig {
    private final String stagingChannelPattern;
    private final String overviewChannelPattern;
    private final List<String> categories;
    private final String categoryRoleSuffix;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private HelpSystemConfig(
            @JsonProperty(value = "stagingChannelPattern",
                    required = true) String stagingChannelPattern,
            @JsonProperty(value = "overviewChannelPattern",
                    required = true) String overviewChannelPattern,
            @JsonProperty(value = "categories", required = true) List<String> categories,
            @JsonProperty(value = "categoryRoleSuffix",
                    required = true) String categoryRoleSuffix) {
        this.stagingChannelPattern = Objects.requireNonNull(stagingChannelPattern);
        this.overviewChannelPattern = Objects.requireNonNull(overviewChannelPattern);
        this.categories = new ArrayList<>(Objects.requireNonNull(categories));
        this.categoryRoleSuffix = Objects.requireNonNull(categoryRoleSuffix);
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
