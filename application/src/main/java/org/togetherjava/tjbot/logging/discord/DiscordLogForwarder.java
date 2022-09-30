package org.togetherjava.tjbot.logging.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.logging.LogMarkers;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Forwards log events to a Discord channel via a webhook. See {@link #forwardLogEvent(LogEvent)}.
 * <p>
 * Logs are forwarded in correct order, based on their timestamp. They are not forwarded
 * immediately, but at a fixed schedule in batches of {@value MAX_BATCH_SIZE} logs.
 * <p>
 * Although unlikely to hit, the class maximally buffers {@value MAX_PENDING_LOGS} logs until
 * discarding further logs. Under normal circumstances, the class can easily handle high loads of
 * logs.
 * <p>
 * The class is thread-safe.
 */
final class DiscordLogForwarder {
    private static final Logger logger = LoggerFactory.getLogger(DiscordLogForwarder.class);

    private static final int MAX_PENDING_LOGS = 10_000;
    private static final int MAX_BATCH_SIZE = WebhookMessage.MAX_EMBEDS;
    private static final ScheduledExecutorService SERVICE =
            Executors.newSingleThreadScheduledExecutor();
    /**
     * Has to be small enough for fitting all {@value MAX_BATCH_SIZE} embeds contained in a batch
     * into the total character length of ~6000.
     */
    private static final int MAX_EMBED_DESCRIPTION = 1_000;
    private static final Map<Level, Integer> LEVEL_TO_AMBIENT_COLOR =
            Map.of(Level.TRACE, 0x00B362, Level.DEBUG, 0x00A5CE, Level.INFO, 0xAC59FF, Level.WARN,
                    0xDFDF00, Level.ERROR, 0xBF2200, Level.FATAL, 0xFF8484);

    private final WebhookClient webhookClient;
    /**
     * Internal buffer of logs that still have to be forwarded to Discord. Actions are synchronized
     * using {@link #pendingLogsLock} to ensure thread safety.
     */
    private final Queue<LogMessage> pendingLogs = new PriorityQueue<>();
    private final Object pendingLogsLock = new Object();

    DiscordLogForwarder(URI webhook) {
        webhookClient = WebhookClient.withUrl(webhook.toString());

        SERVICE.scheduleWithFixedDelay(this::processPendingLogs, 5, 5, TimeUnit.SECONDS);
    }


    /**
     * Forwards the given log message to Discord.
     * <p>
     * Logs are not immediately forwarded, but on a schedule. If the maximal buffer size of
     * {@value MAX_PENDING_LOGS} is exceeded, logs are discarded.
     * <p>
     * This method is thread-safe.
     *
     * @param event the log to forward
     */
    void forwardLogEvent(LogEvent event) {
        if (pendingLogs.size() >= MAX_PENDING_LOGS) {
            logger.warn(LogMarkers.NO_DISCORD,
                    """
                            Exceeded the max amount of logs that can be buffered. \
                            Logs are forwarded to Discord slower than they pile up. Discarding the latest log...""");
            return;
        }

        LogMessage log = LogMessage.ofEvent(event);

        synchronized (pendingLogsLock) {
            pendingLogs.add(log);
        }
    }

    private void processPendingLogs() {
        try {
            // Process batch
            List<LogMessage> logsToProcess = pollLogsToProcessBatch();
            if (logsToProcess.isEmpty()) {
                return;
            }

            List<WebhookEmbed> logBatch = logsToProcess.stream().map(LogMessage::embed).toList();

            webhookClient.send(logBatch);
        } catch (Exception e) {
            logger.warn(LogMarkers.NO_DISCORD,
                    "Unknown error when forwarding pending logs to Discord.", e);
        }
    }

    private List<LogMessage> pollLogsToProcessBatch() {
        int batchSize = Math.min(pendingLogs.size(), MAX_BATCH_SIZE);
        synchronized (pendingLogsLock) {
            return Stream.generate(pendingLogs::remove).limit(batchSize).toList();
        }
    }

    private record LogMessage(WebhookEmbed embed,
            Instant timestamp) implements Comparable<LogMessage> {

        private static LogMessage ofEvent(LogEvent event) {
            String authorName = event.getLoggerName();
            String title = event.getLevel().name();
            int colorDecimal = Objects.requireNonNull(LEVEL_TO_AMBIENT_COLOR.get(event.getLevel()));
            String description =
                    abbreviate(event.getMessage().getFormattedMessage(), MAX_EMBED_DESCRIPTION);
            Instant timestamp = Instant.ofEpochMilli(event.getInstant().getEpochMillisecond());

            WebhookEmbed embed = new WebhookEmbedBuilder()
                .setAuthor(new WebhookEmbed.EmbedAuthor(authorName, null, null))
                .setTitle(new WebhookEmbed.EmbedTitle(title, null))
                .setDescription(description)
                .setColor(colorDecimal)
                .setTimestamp(timestamp)
                .build();

            return new LogMessage(embed, timestamp);
        }

        private static String abbreviate(String text, int maxLength) {
            if (text.length() < maxLength) {
                return text;
            }

            if (maxLength < 3) {
                return text.substring(0, maxLength);
            }

            return text.substring(0, maxLength - 3) + "...";
        }

        @Override
        public int compareTo(@NotNull LogMessage o) {
            return timestamp.compareTo(o.timestamp);
        }
    }
}
