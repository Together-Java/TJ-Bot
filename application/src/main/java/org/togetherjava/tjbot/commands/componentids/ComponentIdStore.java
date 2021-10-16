package org.togetherjava.tjbot.commands.componentids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import org.apache.commons.collections4.map.LRUMap;
import org.jetbrains.annotations.NotNull;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ComponentIds;
import org.togetherjava.tjbot.db.generated.tables.records.ComponentIdsRecord;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ComponentIdStore
        implements ComponentIdGenerator, ComponentIdParser, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ComponentIdStore.class);
    private static final CsvMapper CSV = new CsvMapper();
    private static final long EVICT_EVERY_INITIAL_DELAY = 1;
    private static final long EVICT_EVERY_DELAY = 15;
    private static final ChronoUnit EVICT_EVERY_UNIT = ChronoUnit.MINUTES;
    private static final long EVICT_OLDER_THAN = 20;
    private static final ChronoUnit EVICT_OLDER_THAN_UNIT = ChronoUnit.DAYS;
    private static final int IN_MEMORY_CACHE_SIZE = 1_000;

    private final Database database;
    private final Map<UUID, ComponentId> uuidToComponentId =
            Collections.synchronizedMap(new LRUMap<>(IN_MEMORY_CACHE_SIZE));
    private final Collection<Consumer<ComponentId>> componentIdRemovedListeners =
            Collections.synchronizedCollection(new ArrayList<>());
    private final ExecutorService heatService = Executors.newCachedThreadPool();
    private final ExecutorService componentIdRemovedListenerService =
            Executors.newCachedThreadPool();
    private final ScheduledExecutorService evictionService =
            Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> evictionTask;
    private final long evictOlderThan;
    private final TemporalUnit evictOlderThanUnit;

    // NOTE The class needs no further extra synchronization to be thread-safe.
    // It only uses thread-safe collections and the database is also thread-safe.
    // It does not hurt if the in-memory cache and the database differ slightly momentarily
    // due to multi-threaded manipulation.

    public ComponentIdStore(@NotNull Database database) {
        this(database, EVICT_EVERY_INITIAL_DELAY, EVICT_EVERY_DELAY, EVICT_EVERY_UNIT,
                EVICT_OLDER_THAN, EVICT_OLDER_THAN_UNIT);
    }

    public ComponentIdStore(@NotNull Database database, long evictEveryInitialDelay,
            long evictEveryDelay, ChronoUnit evictEveryUnit, long evictOlderThan,
            ChronoUnit evictOlderThanUnit) {
        this.database = database;
        this.evictOlderThan = evictOlderThan;
        this.evictOlderThanUnit = evictOlderThanUnit;
        evictionTask = evictionService.scheduleWithFixedDelay(this::evictDatabase,
                evictEveryInitialDelay, evictEveryDelay, TimeUnit.of(evictEveryUnit));

        logDebugSizeStatistics();
    }

    @Override
    public @NotNull String generate(@NotNull ComponentId componentId, @NotNull Lifespan lifespan) {
        UUID uuid = UUID.randomUUID();
        putOrThrow(uuid, componentId, lifespan);
        return uuid.toString();
    }

    @Override
    public @NotNull Optional<ComponentId> parse(@NotNull String uuid) {
        return get(UUID.fromString(uuid));
    }

    public void addComponentIdRemovedListener(@NotNull Consumer<ComponentId> listener) {
        componentIdRemovedListeners.add(listener);
    }

    public @NotNull Optional<ComponentId> get(@NotNull UUID uuid) {
        Optional<ComponentId> componentId =
                Optional.ofNullable(uuidToComponentId.get(uuid)).or(() -> getFromDatabase(uuid));

        if (componentId.isPresent()) {
            heatService.execute(() -> heatRecord(uuid));
        }

        return componentId;
    }

    public void putOrThrow(@NotNull UUID uuid, @NotNull ComponentId componentId,
            @NotNull Lifespan lifespan) {
        Supplier<String> alreadyExistsMessageSupplier =
                () -> "The UUID '%s' already exists and is associated to a component id."
                    .formatted(uuid);

        uuidToComponentId.merge(uuid, componentId, (oldValue, nextValue) -> {
            throw new IllegalArgumentException(alreadyExistsMessageSupplier.get());
        });

        boolean alreadyExists = database.write(context -> {
            ComponentIdsRecord componentIdsRecord = context.newRecord(ComponentIds.COMPONENT_IDS)
                .setUuid(uuid.toString())
                .setComponentId(serializeComponentId(componentId))
                .setLastUsed(Instant.now())
                .setLifespan(lifespan.name());
            if (componentIdsRecord.update() == 1) {
                return true;
            }
            componentIdsRecord.insert();
            return false;
        });
        if (alreadyExists) {
            throw new IllegalArgumentException(alreadyExistsMessageSupplier.get());
        }
    }

    private @NotNull Optional<ComponentId> getFromDatabase(@NotNull UUID uuid) {
        return database.read(context -> {
            try (var select = context.selectFrom(ComponentIds.COMPONENT_IDS)) {
                return Optional
                    .ofNullable(select.where(ComponentIds.COMPONENT_IDS.UUID.eq(uuid.toString()))
                        .fetchOne())
                    .map(ComponentIdsRecord::getComponentId)
                    .map(ComponentIdStore::deserializeComponentId);
            }
        });
    }

    private void heatRecord(@NotNull UUID uuid) {
        int updatedRecords = database.write(context -> {
            try (var set = context.update(ComponentIds.COMPONENT_IDS)
                .set(ComponentIds.COMPONENT_IDS.LAST_USED, Instant.now())) {
                return set.where(ComponentIds.COMPONENT_IDS.UUID.eq(uuid.toString())).execute();
            }
        });

        if (updatedRecords == 0) {
            throw new IllegalArgumentException(
                    "Can not heat a record that does not exist, the UUID '%s' was not found"
                        .formatted(uuid));
        }
        if (updatedRecords > 1) {
            throw new AssertionError(
                    "Multiple records had the UUID '%s' even though it is unique.".formatted(uuid));
        }
    }

    private void evictDatabase() {
        // Find old entries that are not permanent
        logger.debug("Evicting old non-permanent component ids from the database...");
        AtomicInteger evictedCounter = new AtomicInteger(0);
        database.write(context -> {
            try (var selectFrom = context.selectFrom(ComponentIds.COMPONENT_IDS)) {
                Result<ComponentIdsRecord> oldRecords = selectFrom
                    .where(ComponentIds.COMPONENT_IDS.LIFESPAN.notEqual(Lifespan.PERMANENT.name())
                        .and(ComponentIds.COMPONENT_IDS.LAST_USED
                            .lessOrEqual(Instant.now().minus(evictOlderThan, evictOlderThanUnit))))
                    .fetch();
                oldRecords.forEach(recordToDelete -> {
                    UUID uuid = UUID
                        .fromString(recordToDelete.getValue(ComponentIds.COMPONENT_IDS.UUID));
                    ComponentId componentId = deserializeComponentId(
                            recordToDelete.getValue(ComponentIds.COMPONENT_IDS.COMPONENT_ID));
                    Instant lastUsed = recordToDelete.getLastUsed();

                    recordToDelete.delete();
                    evictedCounter.getAndIncrement();
                    logger.debug(
                            "Evicted component id with uuid '{}' from command '{}', last used '{}'",
                            uuid, componentId.commandName(), lastUsed);

                    // Remove them from the in-memory map if still in there
                    uuidToComponentId.remove(uuid);
                    // Notify all listeners, but non-blocking to not delay eviction
                    componentIdRemovedListeners
                        .forEach(listener -> componentIdRemovedListenerService
                            .execute(() -> listener.accept(componentId)));
                });
            }
        });

        if (evictedCounter.get() != 0) {
            logger.info("Evicted {} old non-permanent component ids from the database",
                    evictedCounter.get());
        }
    }

    private static @NotNull String serializeComponentId(@NotNull ComponentId componentId) {
        try {
            return CSV.writerFor(ComponentId.class)
                .with(CSV.schemaFor(ComponentId.class))
                .writeValueAsString(componentId);
        } catch (JsonProcessingException e) {
            throw new InvalidComponentIdFormatException(e);
        }
    }

    private static @NotNull ComponentId deserializeComponentId(@NotNull String componentId) {
        try {
            return CSV.readerFor(ComponentId.class)
                .with(CSV.schemaFor(ComponentId.class))
                .readValue(componentId);
        } catch (JsonProcessingException e) {
            throw new InvalidComponentIdFormatException(e);
        }
    }

    private void logDebugSizeStatistics() {
        if (!logger.isDebugEnabled()) {
            return;
        }

        // Without curly braces on the lambda, the call would be ambiguous
        @SuppressWarnings("java:S1602")
        Map<Lifespan, Integer> lifespanToCount = Arrays.stream(Lifespan.values())
            .collect(Collectors.toMap(Function.identity(), lifespan -> database.read(context -> {
                return context.fetchCount(ComponentIds.COMPONENT_IDS,
                        ComponentIds.COMPONENT_IDS.LIFESPAN.eq(lifespan.name()));
            })));
        int recordsCount = lifespanToCount.values().stream().mapToInt(Integer::intValue).sum();

        logger.debug("The component id store consists of {} records ({})", recordsCount,
                lifespanToCount);
    }

    @Override
    public void close() {
        heatService.shutdown();
        if (evictionTask != null) {
            evictionTask.cancel(false);
        }
        evictionService.shutdown();
        componentIdRemovedListenerService.shutdown();
    }
}
