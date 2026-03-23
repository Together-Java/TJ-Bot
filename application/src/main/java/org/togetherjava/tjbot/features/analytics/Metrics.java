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
     * Track an event execution with custom dimensions.
     *
     * @param event the event to save
     * @param dimensions key-value pairs providing additional context for the event
     */
    public void count(String event, Map<String, String> dimensions) {
        logger.debug("Counting new record for event: {}", event);

        Instant happenedAt = Instant.now();
        String serializedDimensions = serializeDimensions(dimensions);

        service.submit(() -> processEvent(event, happenedAt,
                dimensions.isEmpty() ? null : serializedDimensions));
    }

    private static String serializeDimensions(Map<String, String> dimensions) {
        try {
            return OBJECT_MAPPER.writeValueAsString(dimensions);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize dimensions", e);
        }
    }

    /**
     *
     * @param event the event to save
     * @param happenedAt the moment when the event is dispatched
     * @param dimensionsJson optional JSON-serialized dimensions, or null
     */
    private void processEvent(String event, Instant happenedAt, @Nullable String dimensionsJson) {
        database.write(context -> context.newRecord(MetricEvents.METRIC_EVENTS)
            .setEvent(event)
            .setHappenedAt(happenedAt)
            .setDimensions(dimensionsJson)
            .insert());
    }

}
