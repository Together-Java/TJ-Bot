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
    private final String githubApiKey;
    private final String databasePath;
    private final String projectWebsite;
    private final String discordGuildInvite;
    private final String modAuditLogChannelPattern;
    private final String modMailChannelPattern;
    private final String projectsChannelPattern;
    private final String mutedRolePattern;
    private final String heavyModerationRolePattern;
    private final String softModerationRolePattern;
    private final String tagManageRolePattern;
    private final String excludeCodeAutoDetectionRolePattern;
    private final SuggestionsConfig suggestions;
    private final String quarantinedRolePattern;
    private final ScamBlockerConfig scamBlocker;
    private final String wolframAlphaAppId;
    private final HelpSystemConfig helpSystem;
    private final List<String> blacklistedFileExtension;
    private final String mediaOnlyChannelPattern;
    private final String logInfoChannelWebhook;
    private final String logErrorChannelWebhook;
    private final String githubReferencingEnabledChannelPattern;
    private final List<Long> githubRepositories;
    private final String openaiApiKey;
    private final String sourceCodeBaseUrl;
    private final JShellConfig jshell;
    private final HelperPruneConfig helperPruneConfig;
    private final FeatureBlacklistConfig featureBlacklistConfig;
    private final RSSFeedsConfig rssFeedsConfig;
    private final String selectRolesChannelPattern;
    private final String memberCountCategoryPattern;

    @SuppressWarnings("ConstructorWithTooManyParameters")
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private Config(@JsonProperty(value = "token", required = true) String token,
            @JsonProperty(value = "githubApiKey", required = true) String githubApiKey,
            @JsonProperty(value = "databasePath", required = true) String databasePath,
            @JsonProperty(value = "projectWebsite", required = true) String projectWebsite,
            @JsonProperty(value = "discordGuildInvite", required = true) String discordGuildInvite,
            @JsonProperty(value = "modAuditLogChannelPattern",
                    required = true) String modAuditLogChannelPattern,
            @JsonProperty(value = "modMailChannelPattern",
                    required = true) String modMailChannelPattern,
            @JsonProperty(value = "projectsChannelPattern",
                    required = true) String projectsChannelPattern,
            @JsonProperty(value = "mutedRolePattern", required = true) String mutedRolePattern,
            @JsonProperty(value = "heavyModerationRolePattern",
                    required = true) String heavyModerationRolePattern,
            @JsonProperty(value = "softModerationRolePattern",
                    required = true) String softModerationRolePattern,
            @JsonProperty(value = "tagManageRolePattern",
                    required = true) String tagManageRolePattern,
            @JsonProperty(value = "excludeCodeAutoDetectionRolePattern",
                    required = true) String excludeCodeAutoDetectionRolePattern,
            @JsonProperty(value = "suggestions", required = true) SuggestionsConfig suggestions,
            @JsonProperty(value = "quarantinedRolePattern",
                    required = true) String quarantinedRolePattern,
            @JsonProperty(value = "scamBlocker", required = true) ScamBlockerConfig scamBlocker,
            @JsonProperty(value = "wolframAlphaAppId", required = true) String wolframAlphaAppId,
            @JsonProperty(value = "helpSystem", required = true) HelpSystemConfig helpSystem,
            @JsonProperty(value = "mediaOnlyChannelPattern",
                    required = true) String mediaOnlyChannelPattern,
            @JsonProperty(value = "blacklistedFileExtension",
                    required = true) List<String> blacklistedFileExtension,
            @JsonProperty(value = "logInfoChannelWebhook",
                    required = true) String logInfoChannelWebhook,
            @JsonProperty(value = "logErrorChannelWebhook",
                    required = true) String logErrorChannelWebhook,
            @JsonProperty(value = "githubReferencingEnabledChannelPattern",
                    required = true) String githubReferencingEnabledChannelPattern,
            @JsonProperty(value = "githubRepositories",
                    required = true) List<Long> githubRepositories,
            @JsonProperty(value = "openaiApiKey", required = true) String openaiApiKey,
            @JsonProperty(value = "sourceCodeBaseUrl", required = true) String sourceCodeBaseUrl,
            @JsonProperty(value = "jshell", required = true) JShellConfig jshell,
            @JsonProperty(value = "memberCountCategoryPattern",
                    required = true) String memberCountCategoryPattern,
            @JsonProperty(value = "helperPruneConfig",
                    required = true) HelperPruneConfig helperPruneConfig,
            @JsonProperty(value = "featureBlacklist",
                    required = true) FeatureBlacklistConfig featureBlacklistConfig,
            @JsonProperty(value = "rssConfig", required = true) RSSFeedsConfig rssFeedsConfig,
            @JsonProperty(value = "selectRolesChannelPattern",
                    required = true) String selectRolesChannelPattern) {
        this.token = Objects.requireNonNull(token);
        this.githubApiKey = Objects.requireNonNull(githubApiKey);
        this.databasePath = Objects.requireNonNull(databasePath);
        this.projectWebsite = Objects.requireNonNull(projectWebsite);
        this.memberCountCategoryPattern = Objects.requireNonNull(memberCountCategoryPattern);
        this.discordGuildInvite = Objects.requireNonNull(discordGuildInvite);
        this.modAuditLogChannelPattern = Objects.requireNonNull(modAuditLogChannelPattern);
        this.modMailChannelPattern = Objects.requireNonNull(modMailChannelPattern);
        this.projectsChannelPattern = Objects.requireNonNull(projectsChannelPattern);
        this.mutedRolePattern = Objects.requireNonNull(mutedRolePattern);
        this.heavyModerationRolePattern = Objects.requireNonNull(heavyModerationRolePattern);
        this.softModerationRolePattern = Objects.requireNonNull(softModerationRolePattern);
        this.tagManageRolePattern = Objects.requireNonNull(tagManageRolePattern);
        this.excludeCodeAutoDetectionRolePattern =
                Objects.requireNonNull(excludeCodeAutoDetectionRolePattern);
        this.suggestions = Objects.requireNonNull(suggestions);
        this.quarantinedRolePattern = Objects.requireNonNull(quarantinedRolePattern);
        this.scamBlocker = Objects.requireNonNull(scamBlocker);
        this.wolframAlphaAppId = Objects.requireNonNull(wolframAlphaAppId);
        this.helpSystem = Objects.requireNonNull(helpSystem);
        this.mediaOnlyChannelPattern = Objects.requireNonNull(mediaOnlyChannelPattern);
        this.blacklistedFileExtension = Objects.requireNonNull(blacklistedFileExtension);
        this.logInfoChannelWebhook = Objects.requireNonNull(logInfoChannelWebhook);
        this.logErrorChannelWebhook = Objects.requireNonNull(logErrorChannelWebhook);
        this.githubReferencingEnabledChannelPattern =
                Objects.requireNonNull(githubReferencingEnabledChannelPattern);
        this.githubRepositories = Objects.requireNonNull(githubRepositories);
        this.openaiApiKey = Objects.requireNonNull(openaiApiKey);
        this.sourceCodeBaseUrl = Objects.requireNonNull(sourceCodeBaseUrl);
        this.jshell = Objects.requireNonNull(jshell);
        this.helperPruneConfig = Objects.requireNonNull(helperPruneConfig);
        this.featureBlacklistConfig = Objects.requireNonNull(featureBlacklistConfig);
        this.rssFeedsConfig = Objects.requireNonNull(rssFeedsConfig);
        this.selectRolesChannelPattern = Objects.requireNonNull(selectRolesChannelPattern);
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
     * Gets the REGEX pattern used to identify the channel that is supposed to contain all messages
     * from users who want to contact a moderator.
     *
     * @return the channel name pattern
     */
    public String getModMailChannelPattern() {
        return modMailChannelPattern;
    }

    /**
     * Gets the REGEX pattern used to identify the channel that is supposed to contain information
     * about user projects
     *
     * @return the channel name pattern
     */
    public String getProjectsChannelPattern() {
        return projectsChannelPattern;
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
     * Gets the API Key of GitHub.
     *
     * @return the API Key
     * @see <a href=
     *      "https://docs.github.com/en/enterprise-server@3.4/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token">Create
     *      a GitHub key</a>
     */
    public String getGitHubApiKey() {
        return githubApiKey;
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
     * Gets the REGEX pattern used to identify roles that will be ignored for code actions
     * auto-detection
     *
     * @return the REGEX pattern
     */
    public String getExcludeCodeAutoDetectionRolePattern() {
        return excludeCodeAutoDetectionRolePattern;
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

    /**
     * The REGEX pattern used to identify the channels that support GitHub issue referencing.
     */
    public String getGitHubReferencingEnabledChannelPattern() {
        return githubReferencingEnabledChannelPattern;
    }

    /**
     * The list of repositories that are searched when referencing a GitHub issue.
     */
    public List<Long> getGitHubRepositories() {
        return githubRepositories;
    }

    /**
     * The Discord channel webhook for posting log messages with levels INFO, DEBUG and TRACE.
     *
     * @return the webhook URL
     */
    public String getLogInfoChannelWebhook() {
        return logInfoChannelWebhook;
    }

    /**
     * The Discord channel webhook for posting log messages with levels FATAL, ERROR and WARNING.
     *
     * @return the webhook URL
     */
    public String getLogErrorChannelWebhook() {
        return logErrorChannelWebhook;
    }

    /**
     * The OpenAI token needed for communicating with OpenAI ChatGPT.
     *
     * @return the OpenAI API Token
     */
    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    /**
     * The base URL of the source code of this bot. E.g.
     * {@code getSourceCodeBaseUrl() + "/org/togetherjava/tjbot/config/Config.java"} would point to
     * this file.
     *
     * @return the base url of the source code of this bot
     */
    public String getSourceCodeBaseUrl() {
        return sourceCodeBaseUrl;
    }

    /**
     * The configuration about jshell REST API and command/code action settings.
     * 
     * @return the jshell configuration
     */
    public JShellConfig getJshell() {
        return jshell;
    }

    /**
     * Gets the config for automatic pruning of helper roles.
     *
     * @return the configuration
     */
    public HelperPruneConfig getHelperPruneConfig() {
        return helperPruneConfig;
    }

    /**
     * The configuration of blacklisted features.
     * 
     * @return configuration of blacklisted features
     */
    public FeatureBlacklistConfig getFeatureBlacklistConfig() {
        return featureBlacklistConfig;
    }

    /**
     * Gets the REGEX pattern used to identify the channel in which users can select their helper
     * roles.
     *
     * @return the channel name pattern
     */
    public String getSelectRolesChannelPattern() {
        return selectRolesChannelPattern;
    }

    /**
     * Gets the pattern matching the category that is used to display the total member count.
     *
     * @return the categories name types
     */
    public String getMemberCountCategoryPattern() {
        return memberCountCategoryPattern;
    }

    /**
     * Gets the RSS feeds configuration.
     *
     * @return the RSS feeds configuration
     */
    public RSSFeedsConfig getRSSFeedsConfig() {
        return rssFeedsConfig;
    }
}
