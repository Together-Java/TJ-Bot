package org.togetherjava.tjbot.features.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Analytics;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for tracking and recording events for analytics purposes.
 */
public final class Metrics {
    private static final Logger logger = LoggerFactory.getLogger(Metrics.class);

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
        logger.debug("Counting new record for event: {}", event);
        Instant moment = Instant.now();
        service.submit(() -> persist(event, moment));

    }

    /**
     *
     * @param event the event to save
     * @param happenedAt the moment when the event is dispatched
     */
    private void persist(String event, Instant happenedAt) {
        logger.debug("Persisting event: {}, at {}", event, happenedAt);
        database.write(context -> context.newRecord(Analytics.ANALYTICS)
            .setEvent(event)
            .setHappenedAt(happenedAt)
            .insert());
        logger.debug("Event {} persisted successfully", event);
    }

}
