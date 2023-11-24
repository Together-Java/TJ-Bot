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

import java.io.IOException;
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

    /**
     * Starts the application.
     *
     * @param args command line arguments - [the path to the configuration file (optional, by
     *        default "config.json")]
     */
    public static void main(final String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected no or one argument but " + args.length
                    + " arguments were provided. The first argument is the path to the configuration file. If no argument was provided, '"
                    + DEFAULT_CONFIG_PATH + "' will be assumed.");
        }

        Path configPath = Path.of(args.length == 1 ? args[0] : DEFAULT_CONFIG_PATH);
        Config config;
        try {
            config = Config.load(configPath);
        } catch (IOException e) {
            logger.error("Unable to load the configuration file from path '{}'",
                    configPath.toAbsolutePath(), e);
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(Application::onUncaughtException);
        Runtime.getRuntime().addShutdownHook(new Thread(Application::onShutdown));
        DiscordLogging.startDiscordLogging(config);

        runBot(config);
    }

    /**
     * Runs an instance of the bot, connecting to the given token and using the given database.
     *
     * @param config the configuration to run the bot with
     */
    @SuppressWarnings("WeakerAccess")
    public static void runBot(Config config) {
        logger.info("Starting bot...");

        Path databasePath = Path.of(config.getDatabasePath());
        try {
            Path parentDatabasePath = databasePath.toAbsolutePath().getParent();
            if (parentDatabasePath != null) {
                Files.createDirectories(parentDatabasePath);
            }
            Database database = new Database("jdbc:sqlite:" + databasePath.toAbsolutePath());

            JDA jda = JDABuilder.createDefault(config.getToken())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .build();

            jda.awaitReady();

            BotCore core = new BotCore(jda, database, config);
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
