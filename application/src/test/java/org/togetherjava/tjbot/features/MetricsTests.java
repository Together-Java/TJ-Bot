package org.togetherjava.tjbot.features;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.MetricEvents;
import org.togetherjava.tjbot.db.generated.tables.records.MetricEventsRecord;
import org.togetherjava.tjbot.features.analytics.Metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MetricsTests {
    private Database database;
    private Metrics metrics;

    @BeforeEach
    void setUp() {
        database = Database.createMemoryDatabase(MetricEvents.METRIC_EVENTS);
        metrics = new Metrics(database);
    }

    @AfterEach
    void tearDown() {
        metrics.getExecutorService().shutdownNow();
    }

    @Test
    void countWithDoAsyncFalsePersists() {

        String testEvent = "metrics_test_event";

        metrics.count(testEvent, false);

        MetricEventsRecord savedRecord =
                database.read(context -> context.selectFrom(MetricEvents.METRIC_EVENTS).fetchOne());

        assertNotNull(savedRecord);

        assertEquals(testEvent, savedRecord.get(MetricEvents.METRIC_EVENTS.EVENT));
        assertNull(savedRecord.get(MetricEvents.METRIC_EVENTS.DIMENSIONS));
    }

}
