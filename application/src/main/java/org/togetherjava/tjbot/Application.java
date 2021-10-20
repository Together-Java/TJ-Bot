package org.togetherjava.tjbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.Commands;
import org.togetherjava.tjbot.commands.system.CommandSystem;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Main class of the application. Use {@link #main(String[])} to start an instance of it.
 * <p>
 * New commands can be created by implementing
 * {@link net.dv8tion.jda.api.events.interaction.SlashCommandEvent} or extending
 * {@link org.togetherjava.tjbot.commands.SlashCommandAdapter}. They can then be registered in
 * {@link Commands}.
 */
public enum Application {
    ;

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
        try {
            Config.load(configPath);
        } catch (IOException e) {
            logger.error("Unable to load the configuration file from path '{}'",
                    configPath.toAbsolutePath(), e);
            return;
        }

        try {
            Config config = Config.getInstance();
            runBot(config.getToken(), Path.of(config.getDatabasePath()));
        } catch (Exception t) {
            logger.error("Unknown error", t);
        }
    }

    /**
     * Runs an instance of the bot, connecting to the given token and using the given database.
     *
     * @param token the Discord Bot token to connect with
     * @param databasePath the path to the database to use
     */
    @SuppressWarnings("WeakerAccess")
    public static void runBot(String token, Path databasePath) {
        logger.info("Starting bot...");
        try {
            Path parentDatabasePath = databasePath.toAbsolutePath().getParent();
            if (parentDatabasePath != null) {
                Files.createDirectories(parentDatabasePath);
            }
            Database database = new Database("jdbc:sqlite:" + databasePath.toAbsolutePath());

            JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new CommandSystem(database))
                .build();
            jda.awaitReady();
            logger.info("Bot is ready");

            Runtime.getRuntime().addShutdownHook(new Thread(Application::onShutdown));
        } catch (LoginException e) {
            logger.error("Failed to login", e);
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

}
