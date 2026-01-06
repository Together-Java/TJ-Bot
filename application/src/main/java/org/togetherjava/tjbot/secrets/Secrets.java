package org.togetherjava.tjbot.secrets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * This class contains secrets such as API keys, tokens etc., used by the application.
 */
public class Secrets {
    private final String token;
    private final String githubApiKey;
    private final String logInfoChannelWebhook;
    private final String logErrorChannelWebhook;
    private final String openaiApiKey;
    private final String jshellBaseUrl;

    @SuppressWarnings("ConstructorWithTooManyParameters")
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private Secrets(@JsonProperty(value = "token", required = true) String token,
            @JsonProperty(value = "githubApiKey", required = true) String githubApiKey,
            @JsonProperty(value = "logInfoChannelWebhook",
                    required = true) String logInfoChannelWebhook,
            @JsonProperty(value = "logErrorChannelWebhook",
                    required = true) String logErrorChannelWebhook,
            @JsonProperty(value = "openaiApiKey", required = true) String openaiApiKey,
            @JsonProperty(value = "jshellBaseUrl", required = true) String jshellBaseUrl) {
        this.token = Objects.requireNonNull(token);
        this.githubApiKey = Objects.requireNonNull(githubApiKey);
        this.logInfoChannelWebhook = Objects.requireNonNull(logInfoChannelWebhook);
        this.logErrorChannelWebhook = Objects.requireNonNull(logErrorChannelWebhook);
        this.openaiApiKey = Objects.requireNonNull(openaiApiKey);
        this.jshellBaseUrl = Objects.requireNonNull(jshellBaseUrl);
    }

    /**
     * Loads the configuration from the given file.
     *
     * @param path the location to secrets file
     * @return the loaded configuration
     * @throws IOException if the file could not be loaded
     */
    public static Secrets load(Path path) throws IOException {
        return new ObjectMapper().registerModule(new JavaTimeModule())
            .readValue(path.toFile(), Secrets.class);
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
     * The base URL for the jshell REST API.
     *
     * @return the jshell base url
     */
    public String getJshellBaseUrl() {
        return jshellBaseUrl;
    }
}
