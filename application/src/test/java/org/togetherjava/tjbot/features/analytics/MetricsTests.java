package org.togetherjava.tjbot.features.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.MetricEvents;
import org.togetherjava.tjbot.db.generated.tables.records.MetricEventsRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetricsTests {
    private Database database;
    private Metrics metrics;

    @BeforeEach
    void setUp() {
        database = Database.createMemoryDatabase(MetricEvents.METRIC_EVENTS);
        metrics = new Metrics(database);
    }

    @Test
    void countWithDoAsyncFalsePersists() {

        String expectedEvent = "test-event";

        Instant expectedHappenedAt = Instant.now();

        metrics.count(expectedEvent, Map.of(), false);

        MetricEventsRecord eventRecord = Objects.requireNonNull(
                database.read(context -> context.selectFrom(MetricEvents.METRIC_EVENTS).fetchOne()),
                "event not found");

        Instant actualHappenedAt = eventRecord.getHappenedAt();

        assertEquals(expectedEvent, eventRecord.getEvent());
        assertCloseEnough(expectedHappenedAt, actualHappenedAt);
    }

    private void assertCloseEnough(Instant expectedHappenedAt, Instant actualHappenedAt) {

        Duration thresholdDuration = Duration.ofMinutes(1);

        Duration actualDuration = Duration.between(expectedHappenedAt, actualHappenedAt);

        boolean isBelowThreshold = actualDuration.compareTo(thresholdDuration) < 0;

        assertTrue(isBelowThreshold);
    }
}
