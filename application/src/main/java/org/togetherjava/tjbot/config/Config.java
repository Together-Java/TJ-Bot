package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration of the application. Create instances using {@link #load(Path)}.
 */
public final class Config {
    private final String token;
    private final String gistApiKey;
    private final String databasePath;
    private final String projectWebsite;
    private final String discordGuildInvite;
    private final String modAuditLogChannelPattern;
    private final String mutedRolePattern;
    private final String heavyModerationRolePattern;
    private final String softModerationRolePattern;
    private final String tagManageRolePattern;
    private final SuggestionsConfig suggestions;
    private final String quarantinedRolePattern;
    private final ScamBlockerConfig scamBlocker;
    private final String wolframAlphaAppId;
    private final HelpSystemConfig helpSystem;
    private final List<String> blacklistedFileExtension;

    private final String mediaOnlyChannelPattern;

    @SuppressWarnings("ConstructorWithTooManyParameters")
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private Config(@JsonProperty("token") String token,
            @JsonProperty(value = "gistApiKey", required = true) String gistApiKey,
            @JsonProperty(value = "databasePath", required = true) String databasePath,
            @JsonProperty(value = "projectWebsite", required = true) String projectWebsite,
            @JsonProperty(value = "discordGuildInvite", required = true) String discordGuildInvite,
            @JsonProperty(value = "modAuditLogChannelPattern",
                    required = true) String modAuditLogChannelPattern,
            @JsonProperty(value = "mutedRolePattern", required = true) String mutedRolePattern,
            @JsonProperty(value = "heavyModerationRolePattern",
                    required = true) String heavyModerationRolePattern,
            @JsonProperty(value = "softModerationRolePattern",
                    required = true) String softModerationRolePattern,
            @JsonProperty(value = "tagManageRolePattern",
                    required = true) String tagManageRolePattern,
            @JsonProperty(value = "suggestions", required = true) SuggestionsConfig suggestions,
            @JsonProperty(value = "quarantinedRolePattern",
                    required = true) String quarantinedRolePattern,
            @JsonProperty(value = "scamBlocker", required = true) ScamBlockerConfig scamBlocker,
            @JsonProperty(value = "wolframAlphaAppId", required = true) String wolframAlphaAppId,
            @JsonProperty(value = "helpSystem", required = true) HelpSystemConfig helpSystem,
            @JsonProperty(value = "mediaOnlyChannelPattern",
                    required = true) String mediaOnlyChannelPattern,
            @JsonProperty(value = "blacklistedFileExtension",
                    required = true) List<String> blacklistedFileExtension) {
        this.token = Objects.requireNonNull(token);
        this.gistApiKey = Objects.requireNonNull(gistApiKey);
        this.databasePath = Objects.requireNonNull(databasePath);
        this.projectWebsite = Objects.requireNonNull(projectWebsite);
        this.discordGuildInvite = Objects.requireNonNull(discordGuildInvite);
        this.modAuditLogChannelPattern = Objects.requireNonNull(modAuditLogChannelPattern);
        this.mutedRolePattern = Objects.requireNonNull(mutedRolePattern);
        this.heavyModerationRolePattern = Objects.requireNonNull(heavyModerationRolePattern);
        this.softModerationRolePattern = Objects.requireNonNull(softModerationRolePattern);
        this.tagManageRolePattern = Objects.requireNonNull(tagManageRolePattern);
        this.suggestions = Objects.requireNonNull(suggestions);
        this.quarantinedRolePattern = Objects.requireNonNull(quarantinedRolePattern);
        this.scamBlocker = Objects.requireNonNull(scamBlocker);
        this.wolframAlphaAppId = Objects.requireNonNull(wolframAlphaAppId);
        this.helpSystem = Objects.requireNonNull(helpSystem);
        this.mediaOnlyChannelPattern = Objects.requireNonNull(mediaOnlyChannelPattern);
        this.blacklistedFileExtension = Objects.requireNonNull(blacklistedFileExtension);
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
     * Gets the API Key of GitHub to upload pastes via the API.
     *
     * @return the upload services API Key
     * @see <a href=
     *      "https://docs.github.com/en/enterprise-server@3.4/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token">Create
     *      a GitHub key</a>
     */
    public String getGistApiKey() {
        return gistApiKey;
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
     * Gets the config for the suggestion system.
     *
     * @return the suggestion system config
     */
    public SuggestionsConfig getSuggestions() {
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
    public ScamBlockerConfig getScamBlocker() {
        return scamBlocker;
    }

    /**
     * Gets the application ID used to connect to the WolframAlpha API.
     *
     * @return the application ID for the WolframAlpha API
     */
    public String getWolframAlphaAppId() {
        return wolframAlphaAppId;
    }

    /**
     * Gets the config for the help system.
     *
     * @return the help system config
     */
    public HelpSystemConfig getHelpSystem() {
        return helpSystem;
    }

    /**
     * Gets the REGEX pattern used to identify the channel that is supposed to contain only Media.
     *
     * @return the channel name pattern
     */
    public String getMediaOnlyChannelPattern() {
        return mediaOnlyChannelPattern;
    }

    /**
     * Gets a list of all blacklisted file extensions.
     *
     * @return a list of all blacklisted file extensions
     */
    public List<String> getBlacklistedFileExtensions() {
        return Collections.unmodifiableList(blacklistedFileExtension);
    }
}
