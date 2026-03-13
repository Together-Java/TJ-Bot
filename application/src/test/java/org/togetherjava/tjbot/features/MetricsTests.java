package org.togetherjava.tjbot.features;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.MetricEvents;
import org.togetherjava.tjbot.features.analytics.Metrics;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.fail;

final class MetricsTests {
    private static final Logger logger = LoggerFactory.getLogger(MetricsTests.class);

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(3);

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
    void countInsertsSingleEvent() {

        final String slashPing = "slash-ping";

        metrics.count(slashPing);

        awaitRecords(1);

        List<String> recordedEvents = readEventsOrderedById();

        assertThat(recordedEvents).as("Metrics should persist the counted event in insertion order")
            .containsExactly(slashPing);

        assertThat(readLatestEventHappenedAt())
            .as("Metrics should store a recent timestamp for event '%s' (recordedEvents=%s)",
                    slashPing, recordedEvents)
            .isNotNull()
            .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    private void awaitRecords(int expectedAmount) {
        Instant deadline = Instant.now().plus(WAIT_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            if (readRecordCount() == expectedAmount) {
                return;
            }

            LockSupport.parkNanos(Duration.ofMillis(25).toNanos());

            if (Thread.interrupted()) {
                int actualCount = readRecordCount();

                String msg = String.format(
                        "Interrupted while waiting for metrics writes (expectedAmount=%d, actualCount=%d, timeout=%s, events=%s)",
                        expectedAmount, actualCount, WAIT_TIMEOUT, readEventsOrderedById());

                logger.warn(msg);

                fail(msg);
            }
        }

        int actualCount = readRecordCount();

        List<String> recordedEvents = readEventsOrderedById();

        String timeoutMessage = String.format(
                "Timed out waiting for metrics writes (expectedAmount=%d, actualCount=%d, timeout=%s, events=%s)",
                expectedAmount, actualCount, WAIT_TIMEOUT, recordedEvents);

        logger.warn(timeoutMessage);

        assertThat(actualCount).as(timeoutMessage).isEqualTo(expectedAmount);
    }

    private int readRecordCount() {
        return database.read(context -> context.fetchCount(MetricEvents.METRIC_EVENTS));
    }

    private List<String> readEventsOrderedById() {
        return database.read(context -> context.select(MetricEvents.METRIC_EVENTS.EVENT)
            .from(MetricEvents.METRIC_EVENTS)
            .orderBy(MetricEvents.METRIC_EVENTS.ID.asc())
            .fetch(MetricEvents.METRIC_EVENTS.EVENT));

    }

    private Instant readLatestEventHappenedAt() {
        return database.read(context -> context.select(MetricEvents.METRIC_EVENTS.HAPPENED_AT)
            .from(MetricEvents.METRIC_EVENTS)
            .orderBy(MetricEvents.METRIC_EVENTS.ID.desc())
            .limit(1)
            .fetchOne(MetricEvents.METRIC_EVENTS.HAPPENED_AT));
    }
}
