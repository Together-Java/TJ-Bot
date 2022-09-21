package org.togetherjava.tjbot.logging.discord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

import java.net.URI;

// FIXME Javadoc
public final class DiscordLogging {
    private DiscordLogging() {
        throw new UnsupportedOperationException("Utility class");
    }

    // FIXME Javadoc
    public static void appendDiscordLogAppender(String webhook) {
        URI webhookUri;
        try {
            webhookUri = URI.create(webhook);
        } catch (IllegalArgumentException e) {
            // FIXME Some error reporting
            return;
        }

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();

        Appender appender =
                DiscordLogAppender.newBuilder().setName("Discord").setWebhook(webhookUri).build();
        appender.start();
        configuration.addAppender(appender);
        configuration.getRootLogger().addAppender(appender, null, null);

        context.updateLoggers();
    }
}
