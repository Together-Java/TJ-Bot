package org.togetherjava.tjbot.features.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.MetricEvents;

import javax.annotation.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for tracking and recording events for analytics purposes.
 */
public final class Metrics {
    private static final Logger logger = LoggerFactory.getLogger(Metrics.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Database database;

    private final ExecutorService service = Executors.newSingleThreadExecutor();

    /**
     * Creates a new instance.
     *
     * @param database the database to use for storing and retrieving analytics data
     */
    public Metrics(Database database) {
        this.database = database;
    }

    /**
     * Track an event execution.
     *
     * @param event the event to save
     */
    public void count(String event) {
        count(event, Map.of());
    }

    /**
     * Track an event execution with dimensions provided.
     * 
     * @param event the event to save
     * @param dimensions the dimensions to save
     */
    public void count(String event, Map<String, Object> dimensions) {
        count(event, dimensions, true);
    }

    /**
     * Track an event execution with flag for async execution.
     * 
     * @param event the event to save
     * @param doAsync the async flag
     */
    public void count(String event, boolean doAsync) {
        count(event, Map.of(), doAsync);
    }

    /**
     * Track an event execution with additional contextual data.
     *
     * @param event the name of the event to record (e.g. "user_signup", "purchase")
     * @param dimensions optional key-value pairs providing extra context about the event. These are
     *        often referred to as "metadata" and can include things like: userId: "12345", name:
     *        "John Smith", channel_name: "chit-chat" etc. This data helps with filtering, grouping,
     *        and analyzing events later. Note: A value for a metric should be a Java primitive
     *        (String, int, double, long float).
     */
    void count(String event, Map<String, Object> dimensions, boolean doAsync) {
        logger.debug("Counting new record for event: {}", event);

        Instant happenedAt = Instant.now();
        String serializedDimensions = dimensions.isEmpty() ? null : serializeDimensions(dimensions);

        Runnable task = () -> processEvent(event, happenedAt, serializedDimensions);

        if (doAsync) {
            service.submit(task);
        } else {
            task.run();
        }
    }

    private static String serializeDimensions(Map<String, Object> dimensions) {
        try {
            return OBJECT_MAPPER.writeValueAsString(dimensions);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize dimensions", e);
        }
    }

    void processEvent(String event, Instant happenedAt, @Nullable String dimensionsJson) {
        database.write(context -> context.newRecord(MetricEvents.METRIC_EVENTS)
            .setEvent(event)
            .setHappenedAt(happenedAt)
            .setDimensions(dimensionsJson)
            .insert());
    }

    /**
     * Exposes the underlying executor service.
     * <p>
     * Intended for test teardown only.
     *
     * @return the executor service backing this instance
     */
    public ExecutorService getExecutorService() {
        return service;
    }
}
