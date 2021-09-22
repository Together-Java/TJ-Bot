package org.togetherjava.tjbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.CommandHandler;
import org.togetherjava.tjbot.db.Database;

import javax.security.auth.login.LoginException;
import java.nio.file.Path;
import java.sql.SQLException;

/***
 * Main class of the application. Use {@link #main(String[])} to start an instance of it.
 */
public enum Application {
    ;

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    /**
     * Starts the application.
     *
     * @param args command line arguments - [the token of the bot, the path to the database]
     */
    public static void main(final String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected two arguments but " + args.length
                    + " arguments were provided. The first argument must be the token of the bot"
                    + " and the second the path to the database.");
        }
        String token = args[0];
        String databasePath = args[1];

        try {
            runBot(token, Path.of(databasePath));
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
    public static void runBot(String token, Path databasePath) {
        logger.info("Starting bot...");
        try {
            Database database = new Database("jdbc:sqlite:" + databasePath.toAbsolutePath());

            JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new PingPongListener())
                .addEventListeners(new DatabaseListener(database))
                .addEventListeners(new CommandHandler())
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
        }
    }

    private static void onShutdown() {
        logger.info("Bot has been stopped");
    }
}
