package org.togetherjava.tjbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.Features;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.system.BotCore;
import org.togetherjava.tjbot.logging.LogMarkers;
import org.togetherjava.tjbot.logging.discord.DiscordLogging;
import org.togetherjava.tjbot.secrets.Secrets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Main class of the application. Use {@link #main(String[])} to start an instance of it.
 * <p>
 * New commands can be created by implementing {@link SlashCommandInteractionEvent} or extending
 * {@link SlashCommandAdapter}. They can then be registered in {@link Features}.
 */
public class Application {
    private Application() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String DEFAULT_CONFIG_PATH = "config.json";
    private static final String DEFAULT_SECRETS_PATH = "secrets.json";

    /**
     * Starts the application.
     * <p>
     * Note: By default the configuration file will be loaded from a config.json unless overridden
     * by either: 1. Setting the USE_INCLUDED_CONFIG environment variable to true, which will use
     * the config.json packed in the built jar. 2. Passing a program argument including the path to
     * the config file.
     *
     * @param args command line arguments - [the path to the configuration file (optional, by
     *        default "config.json")]
     */
    public static void main(final String[] args) {
        boolean useIncludedConfig;
        try {
            useIncludedConfig = Boolean.parseBoolean(System.getenv("USE_INCLUDED_CONFIG"));
            logger.info("Using config.json included in jar");
        } catch (Exception _) {
            useIncludedConfig = false;
        }

        String configPath;

        if (args.length > 0) {
            configPath = args[0];
        } else if (useIncludedConfig) {
            configPath = "/" + DEFAULT_CONFIG_PATH;
        } else {
            configPath = DEFAULT_CONFIG_PATH;
        }

        Config config;
        try {
            config = loadConfig(useIncludedConfig, configPath);
        } catch (IOException e) {
            logger.error("Unable to load the configuration file '{}'", configPath, e);
            return;
        }

        Path secretsPath = Path.of(args.length == 1 ? args[0] : DEFAULT_SECRETS_PATH);
        Secrets secrets;
        try {
            secrets = Secrets.load(secretsPath);
        } catch (IOException e) {
            logger.error("Unable to load the configuration file from path '{}'",
                    secretsPath.toAbsolutePath(), e);
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(Application::onUncaughtException);
        Runtime.getRuntime().addShutdownHook(new Thread(Application::onShutdown));
        DiscordLogging.startDiscordLogging(config, secrets);

        runBot(config, secrets);
    }

    /**
     * Attempts to load the configuration file and return a new {@code Config}.
     *
     * @param useIncludedConfig if the config should be loaded from the resources' directory.
     * @param configPath the location of the config file.
     * @return a new {@code Config} object
     * @throws IOException if the configuration file could not be loaded.
     */
    private static Config loadConfig(boolean useIncludedConfig, String configPath)
            throws IOException {
        return useIncludedConfig ? loadConfigFromResource(configPath)
                : loadConfigFromFile(Path.of(configPath));
    }

    /**
     * Loads a configuration file from the application resources directory.
     *
     * @param configPath the location of the configuration file
     * @return a new {@code Config} object
     * @throws IOException if the configuration file could not be loaded
     */
    private static Config loadConfigFromResource(String configPath) throws IOException {
        try (InputStream stream = Application.class.getResourceAsStream(configPath)) {
            if (stream == null) {
                throw new IOException("InputStream is null when loading " + configPath);
            }
            return Config.load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Loads a configuration file from a specified path.
     *
     * @param configPath the location of the configuration file
     * @return a new {@code Config} object
     * @throws IOException if the configuration file could not be loaded
     */
    private static Config loadConfigFromFile(Path configPath) throws IOException {
        return Config.load(configPath);
    }

    /**
     * Runs an instance of the bot, connecting to the given token and using the given database.
     *
     * @param config the configuration to run the bot with
     * @param secrets the secrets to run the bot with
     */
    @SuppressWarnings("WeakerAccess")
    public static void runBot(Config config, Secrets secrets) {
        logger.info("Starting bot...");

        Path databasePath = Path.of(config.getDatabasePath());
        try {
            Path parentDatabasePath = databasePath.toAbsolutePath().getParent();
            if (parentDatabasePath != null) {
                Files.createDirectories(parentDatabasePath);
            }
            Database database = new Database("jdbc:sqlite:" + databasePath.toAbsolutePath());

            JDA jda = JDABuilder.createDefault(secrets.getToken())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .build();

            jda.awaitReady();

            BotCore core = new BotCore(jda, database, config, secrets);
            CommandReloading.reloadCommands(jda, core);
            core.scheduleRoutines(jda);

            jda.addEventListener(core);

            logger.info("Bot is ready");
        } catch (InvalidTokenException e) {
            logger.error(LogMarkers.SENSITIVE, "Failed to login", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for setup to complete", e);
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            logger.error("Failed to create database", e);
        } catch (IOException e) {
            logger.error("Failed to create path to the database at: {}",
                    databasePath.toAbsolutePath(), e);
        }
    }

    private static void onShutdown() {
        // This may be called during JVM shutdown via a hook and hence only has minimal time to
        // react.
        // There is no guarantee that this method can be executed fully - it should run as
        // fast as possible and only do the minimal necessary actions.
        logger.info("Bot has been stopped");
    }

    private static void onUncaughtException(Thread failingThread, Throwable failure) {
        logger.error("Unknown error in thread {}.", failingThread.getName(), failure);
    }

}
