package org.togetherjava.tjbot.logging.discord;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.logging.LogMarkers;

import java.net.URI;
import java.util.Optional;

// FIXME Javadoc
public final class DiscordLogging {
    private static final Logger logger = LoggerFactory.getLogger(DiscordLogging.class);

    private DiscordLogging() {
        throw new UnsupportedOperationException("Utility class");
    }

    // FIXME Javadoc
    public static void startDiscordLogging(Config botConfig) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration logConfig = context.getConfiguration();

        addAppenders(logConfig, botConfig);

        context.updateLoggers();
    }

    private static void addAppenders(Configuration logConfig, Config botConfig) {
        parseWebhookUri(botConfig.getLogInfoChannelWebhook())
            .ifPresent(webhookUri -> addDiscordLogAppender("DiscordInfo", createInfoRangeFilter(),
                    webhookUri, logConfig));

        parseWebhookUri(botConfig.getLogErrorChannelWebhook())
            .ifPresent(webhookUri -> addDiscordLogAppender("DiscordError", createErrorRangeFilter(),
                    webhookUri, logConfig));
    }

    private static Optional<URI> parseWebhookUri(String webhookUri) {
        try {
            return Optional.of(URI.create(webhookUri));
        } catch (IllegalArgumentException e) {
            logger.warn(LogMarkers.NO_DISCORD,
                    "The webhook URL ({}) in the config is invalid, logs will not be forwarded to Discord.",
                    webhookUri, e);
            return Optional.empty();
        }
    }

    private static void addDiscordLogAppender(String name, Filter filter, URI webhookUri,
            Configuration logConfig) {
        Filter[] filters = {filter, createDenyMarkerFilter(LogMarkers.NO_DISCORD.getName()),
                createDenyMarkerFilter(LogMarkers.SENSITIVE.getName())};

        Appender appender = DiscordLogAppender.newBuilder()
            .setName(name)
            .setWebhook(webhookUri)
            .setFilter(CompositeFilter.createFilters(filters))
            .build();

        appender.start();

        logConfig.addAppender(appender);
        logConfig.getRootLogger().addAppender(appender, null, null);
    }

    private static Filter createDenyMarkerFilter(String markerName) {
        return MarkerFilter.createFilter(markerName, Filter.Result.DENY, Filter.Result.NEUTRAL);
    }

    private static Filter createInfoRangeFilter() {
        return LevelRangeFilter.createFilter(Level.INFO, Level.TRACE, Filter.Result.NEUTRAL,
                Filter.Result.DENY);
    }

    private static Filter createErrorRangeFilter() {
        return LevelRangeFilter.createFilter(Level.FATAL, Level.WARN, Filter.Result.NEUTRAL,
                Filter.Result.DENY);
    }
}
