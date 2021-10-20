package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration of the application, as singleton.
 * <p>
 * Create instances using {@link #load(Path)} and then access them with {@link #getInstance()}.
 */
@SuppressWarnings({"Singleton", "ClassCanBeRecord"})
public final class Config {

    @SuppressWarnings("RedundantFieldInitialization")
    private static Config config = null;

    private final String token;
    private final String databasePath;
    private final String projectWebsite;
    private final String discordGuildInvite;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private Config(@JsonProperty("token") String token,
            @JsonProperty("databasePath") String databasePath,
            @JsonProperty("projectWebsite") String projectWebsite,
            @JsonProperty("discordGuildInvite") String discordGuildInvite) {
        this.token = token;
        this.databasePath = databasePath;
        this.projectWebsite = projectWebsite;
        this.discordGuildInvite = discordGuildInvite;
    }

    /**
     * Loads the configuration from the given file. Will override any previously loaded data.
     * <p>
     * Access the instance using {@link #getInstance()}.
     *
     * @param path the configuration file, as JSON object
     * @throws IOException if the file could not be loaded
     */
    public static void load(Path path) throws IOException {
        config = new ObjectMapper().readValue(path.toFile(), Config.class);
    }

    /**
     * Gets the singleton instance of the configuration.
     * <p>
     * Must be loaded beforehand using {@link #load(Path)}.
     *
     * @return the previously loaded configuration
     */
    public static Config getInstance() {
        return Objects.requireNonNull(config,
                "can not get the configuration before it has been loaded");
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
}
