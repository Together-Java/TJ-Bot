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

/**
 * Provides solutions for forwarding logs to Discord. See {@link #startDiscordLogging(Config)}.
 */
public final class DiscordLogging {
    private static final Logger logger = LoggerFactory.getLogger(DiscordLogging.class);

    private DiscordLogging() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Sets up and starts the log forwarding to Discord.
     * <p>
     * Disables the feature if the config is set up incorrectly.
     *
     * @param botConfig to get the logging details from, such as the Discord webhook urls
     */
    public static void startDiscordLogging(Config botConfig) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration logConfig = context.getConfiguration();

        addAppenders(logConfig, botConfig);

        context.updateLoggers();
    }

    private static void addAppenders(Configuration logConfig, Config botConfig) {
        parseWebhookUri(botConfig.getLogInfoChannelWebhook())
            .ifPresent(webhookUri -> addDiscordLogAppender("DiscordInfo", createInfoRangeFilter(),
                    webhookUri, botConfig.getSourceCodeBaseUrl(), logConfig));

        parseWebhookUri(botConfig.getLogErrorChannelWebhook())
            .ifPresent(webhookUri -> addDiscordLogAppender("DiscordError", createErrorRangeFilter(),
                    webhookUri, botConfig.getSourceCodeBaseUrl(), logConfig));
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

    // Security warning about configuring logs. It is safe in this case, the only user input are the
    // webhook URIs, which cannot inject anything malicious.
    // The only risk is changing the target to an attackers' server, but therefore they need access
    // to the config.
    @SuppressWarnings("squid:S4792")
    private static void addDiscordLogAppender(String name, Filter filter, URI webhookUri,
            String sourceCodeBaseUrl, Configuration logConfig) {
        // NOTE The whole setup is done programmatically in order to allow the webhooks
        // to be read from the config file
        Filter[] filters = {filter, createDenyMarkerFilter(LogMarkers.NO_DISCORD.getName()),
                createDenyMarkerFilter(LogMarkers.SENSITIVE.getName())};

        Appender appender = DiscordLogAppender.newBuilder()
            .setName(name)
            .setWebhook(webhookUri)
            .setSourceCodeBaseUrl(sourceCodeBaseUrl)
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
