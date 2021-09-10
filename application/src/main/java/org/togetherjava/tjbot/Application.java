package org.togetherjava.tjbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

public enum Application {
    ;

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    /**
     * Starts the application.
     *
     * @param args One argument - the token of the bot
     */
    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument but " + args.length
                    + " arguments were provided. The argument must be the token of the bot.");
        }
        String token = args[0];

        try {
            runBot(token);
        } catch (Exception t) {
            logger.error("Unknown error", t);
        }
    }

    public static void runBot(String token) {
        logger.info("Starting bot...");
        try {
            JDA jda = JDABuilder.createDefault(token)
                                .addEventListeners(new PingPongListener())
                                .addEventListeners(new DatabaseListener())
                                .build();
            jda.awaitReady();
            logger.info("Bot is ready");
        } catch (LoginException e) {
            logger.error("Failed to login", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for setup to complete", e);
            Thread.currentThread().interrupt();
        }
    }
}
