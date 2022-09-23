package org.togetherjava.tjbot.logging.discord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.core.LogEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.logging.discord.api.DiscordLogBatch;
import org.togetherjava.tjbot.logging.discord.api.DiscordLogMessageEmbed;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

// FIXME This class needs some javadoc here and there
final class DiscordLogForwarder {
    private static final int MAX_PENDING_LOGS = 10_000;
    private static final int MAX_BATCH_SIZE = 10;
    private static final int HTTP_STATUS_TOO_MANY_REQUESTS = 429;
    private static final int MAX_RETRIES_UNTIL_DISCARD = 3;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ScheduledExecutorService SERVICE =
            Executors.newSingleThreadScheduledExecutor();

    private final URI webhook;
    private final Queue<LogMessage> pendingLogs = new PriorityQueue<>();
    private final Object pendingLogsLock = new Object();
    private Instant rateLimitExpiresAt;
    private int currentRetries = 0;

    DiscordLogForwarder(URI webhook) {
        this.webhook = webhook;

        SERVICE.scheduleWithFixedDelay(this::processPendingLogs, 5, 5, TimeUnit.SECONDS);
    }

    void forwardLogEvent(LogEvent event) {
        if (pendingLogs.size() >= MAX_PENDING_LOGS) {
            // FIXME Discard log, some error reporting
            return;
        }

        LogMessage log = LogMessage.ofEvent(event);

        synchronized (pendingLogsLock) {
            pendingLogs.add(log);
        }
    }

    private void processPendingLogs() {
        try {
            if (rateLimitExpiresAt != null) {
                if (Instant.now().isBefore(rateLimitExpiresAt)) {
                    // Still rate limited, try again later
                    return;
                }

                // Rate limit has expired, try again
                rateLimitExpiresAt = null;
            }

            // Process batch
            List<LogMessage> logsToProcess = pollLogsToProcessBatch();
            if (logsToProcess.isEmpty()) {
                return;
            }

            List<DiscordLogMessageEmbed> embeds =
                    logsToProcess.stream().map(LogMessage::embed).toList();
            DiscordLogBatch logBatch = new DiscordLogBatch(embeds);

            LogSendResult result = sendLogBatch(logBatch);
            if (result != LogSendResult.SUCCESS) {
                currentRetries++;
                if (currentRetries <= MAX_RETRIES_UNTIL_DISCARD) {
                    synchronized (pendingLogsLock) {
                        // Try again later
                        pendingLogs.addAll(logsToProcess);
                    }
                } else {
                    // Discarding problematic batch
                    // FIXME some error reporting
                }
            } else {
                currentRetries = 0;
            }
        } catch (Exception e) {
            // FIXME some error reporting
        }
    }

    private List<LogMessage> pollLogsToProcessBatch() {
        int batchSize = Math.min(pendingLogs.size(), MAX_BATCH_SIZE);
        synchronized (pendingLogsLock) {
            return Stream.generate(pendingLogs::remove).limit(batchSize).toList();
        }
    }

    private LogSendResult sendLogBatch(DiscordLogBatch logBatch) {
        String rawPayload;
        try {
            rawPayload = JSON.writeValueAsString(logBatch);
        } catch (JsonProcessingException e) {
            // FIXME Some error reporting
            throw new IllegalArgumentException(e);
        }

        HttpRequest request = HttpRequest.newBuilder(webhook)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(rawPayload))
            .build();

        try {
            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HTTP_STATUS_TOO_MANY_REQUESTS) {
                response.headers()
                    .firstValueAsLong("Retry-After")
                    .ifPresent(retryAfterSeconds -> rateLimitExpiresAt =
                            Instant.now().plusSeconds(retryAfterSeconds));
                // FIXME Some logging
                return LogSendResult.RATE_LIMIT;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // FIXME Some logging
                return LogSendResult.ERROR;
            }

            return LogSendResult.SUCCESS;
        } catch (IOException e) {
            // FIXME Some logging
            return LogSendResult.ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LogSendResult.ERROR;
        }
    }

    private record LogMessage(DiscordLogMessageEmbed embed,
            Instant timestamp) implements Comparable<LogMessage> {
        private static LogMessage ofEvent(LogEvent event) {
            DiscordLogMessageEmbed embed = DiscordLogMessageEmbed.ofEvent(event);
            Instant timestamp = Instant.ofEpochMilli(event.getInstant().getEpochMillisecond());

            return new LogMessage(embed, timestamp);
        }

        @Override
        public int compareTo(@NotNull LogMessage o) {
            return timestamp.compareTo(o.timestamp);
        }
    }


    private enum LogSendResult {
        SUCCESS,
        RATE_LIMIT,
        ERROR
    }
}
