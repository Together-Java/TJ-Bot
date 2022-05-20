package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Configuration of the application. Create instances using {@link #load(Path)}.
 */
public final class Config {
    private final String token;
    private final String databasePath;
    private final String projectWebsite;
    private final String discordGuildInvite;
    private final String modAuditLogChannelPattern;
    private final String mutedRolePattern;
    private final String heavyModerationRolePattern;
    private final String softModerationRolePattern;
    private final String tagManageRolePattern;
    private final List<FreeCommandConfig> freeCommand;
    private final String helpChannelPattern;
    private final SuggestionsConfig suggestions;
    private final String quarantinedRolePattern;
    private final ScamBlockerConfig scamBlocker;
    private final String wolframAlphaAppId;

    @SuppressWarnings("ConstructorWithTooManyParameters")
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private Config(@JsonProperty("token") String token,
            @JsonProperty("databasePath") String databasePath,
            @JsonProperty("projectWebsite") String projectWebsite,
            @JsonProperty("discordGuildInvite") String discordGuildInvite,
            @JsonProperty("modAuditLogChannelPattern") String modAuditLogChannelPattern,
            @JsonProperty("mutedRolePattern") String mutedRolePattern,
            @JsonProperty("heavyModerationRolePattern") String heavyModerationRolePattern,
            @JsonProperty("softModerationRolePattern") String softModerationRolePattern,
            @JsonProperty("tagManageRolePattern") String tagManageRolePattern,
            @JsonProperty("freeCommand") List<FreeCommandConfig> freeCommand,
            @JsonProperty("helpChannelPattern") String helpChannelPattern,
            @JsonProperty("suggestions") SuggestionsConfig suggestions,
            @JsonProperty("quarantinedRolePattern") String quarantinedRolePattern,
            @JsonProperty("scamBlocker") ScamBlockerConfig scamBlocker,
            @JsonProperty("wolframAlphaAppId") String wolframAlphaAppId) {
        this.token = token;
        this.databasePath = databasePath;
        this.projectWebsite = projectWebsite;
        this.discordGuildInvite = discordGuildInvite;
        this.modAuditLogChannelPattern = modAuditLogChannelPattern;
        this.mutedRolePattern = mutedRolePattern;
        this.heavyModerationRolePattern = heavyModerationRolePattern;
        this.softModerationRolePattern = softModerationRolePattern;
        this.tagManageRolePattern = tagManageRolePattern;
        this.freeCommand = Collections.unmodifiableList(freeCommand);
        this.helpChannelPattern = helpChannelPattern;
        this.suggestions = suggestions;
        this.quarantinedRolePattern = quarantinedRolePattern;
        this.scamBlocker = scamBlocker;
        this.wolframAlphaAppId = wolframAlphaAppId;
    }

    /**
     * Loads the configuration from the given file.
     *
     * @param path the configuration file, as JSON object
     * @return the loaded configuration
     * @throws IOException if the file could not be loaded
     */
    public static Config load(Path path) throws IOException {
        return new ObjectMapper().registerModule(new JavaTimeModule())
            .readValue(path.toFile(), Config.class);
    }

    /**
     * Gets the REGEX pattern used to identify the role assigned to muted users.
     *
     * @return the role name pattern
     */
    public String getMutedRolePattern() {
        return mutedRolePattern;
    }

    /**
     * Gets the REGEX pattern used to identify the channel that is supposed to contain all mod audit
     * logs.
     *
     * @return the channel name pattern
     */
    public String getModAuditLogChannelPattern() {
        return modAuditLogChannelPattern;
    }

    /**
     * Gets the token of the Discord bot to connect this application to.
     *
     * @return the Discord bot token
     */
    public String getToken() {
        return token;
    }

    /**
     * Gets the path where the database of the application is located at.
     *
     * @return the path of the database
     */
    public String getDatabasePath() {
        return databasePath;
    }

    /**
     * Gets a URL of the project's website, for example to tell the user where he can contribute.
     *
     * @return the website of the project
     */
    public String getProjectWebsite() {
        return projectWebsite;
    }

    /**
     * Gets an invite-URL to join the Discord guild this application is connected to.
     *
     * @return an invite-URL for this Discord guild
     */
    public String getDiscordGuildInvite() {
        return discordGuildInvite;
    }

    /**
     * Gets the REGEX pattern used to identify roles that are allowed to use heavy moderation
     * commands, such as banning, based on role names.
     *
     * @return the REGEX pattern
     */
    public String getHeavyModerationRolePattern() {
        return heavyModerationRolePattern;
    }

    /**
     * Gets the REGEX pattern used to identify roles that are allowed to use soft moderation
     * commands, such as kicking, muting or message deletion, based on role names.
     *
     * @return the REGEX pattern
     */
    public String getSoftModerationRolePattern() {
        return softModerationRolePattern;
    }

    /**
     * Gets the REGEX pattern used to identify roles that are allowed to use the tag-manage command,
     * such as creating or editing tags.
     *
     * @return the REGEX pattern
     */
    public String getTagManageRolePattern() {
        return tagManageRolePattern;
    }

    /**
     * Gets a List of channel id's required to configure the free command system see
     * {@link FreeCommandConfig}
     *
     * @return a List of instances of FreeCommandConfig, each of the instances are separated by
     *         guild.
     */
    public @NotNull Collection<FreeCommandConfig> getFreeCommandConfig() {
        return freeCommand; // already unmodifiable
    }

    /**
     * Gets the REGEX pattern used to identify channels that are used for helping people with their
     * questions.
     *
     * @return the channel name pattern
     */
    public @NotNull String getHelpChannelPattern() {
        return helpChannelPattern;
    }

    /**
     * Gets the config for the suggestion system.
     *
     * @return the suggestion system config
     */
    public @NotNull SuggestionsConfig getSuggestions() {
        return suggestions;
    }

    /**
     * Gets the REGEX pattern used to identify the role assigned to quarantined users.
     *
     * @return the role name pattern
     */
    public String getQuarantinedRolePattern() {
        return quarantinedRolePattern;
    }

    /**
     * Gets the config for the scam blocker system.
     *
     * @return the scam blocker system config
     */
    public @NotNull ScamBlockerConfig getScamBlocker() {
        return scamBlocker;
    }

    /**
     * Gets the application ID used to connect to the WolframAlpha API.
     *
     * @return the application ID for the WolframAlpha API
     */
    public @NotNull String getWolframAlphaAppId() {
        return wolframAlphaAppId;
    }
}
