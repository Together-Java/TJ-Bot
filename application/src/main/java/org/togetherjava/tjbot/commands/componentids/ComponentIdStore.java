package org.togetherjava.tjbot.commands.componentids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
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

/**
 * Thread-safe storage for component IDs. Can generate, persist and parse component IDs based on
 * UUIDs. Component IDs are used for button and selection menu commands, see
 * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)} for
 * details.
 * <p>
 * Use {@link #generate(ComponentId, Lifespan)} to generate and persist a component ID; and
 * {@link #parse(String)} to get it back. Component IDs are persisted during application runs and
 * can hence be retrieved back even after long times.
 * <p>
 * The store also provides a more direct interface to its functionality, using {@link #get(UUID)}
 * and {@link #putOrThrow(UUID, ComponentId, Lifespan)}.
 * <p>
 * <p>
 * Component IDs which have not been used for a long time, depending on their {@link Lifespan}
 * setting, might get evicted from the store after some time. The store implements a **LRU-cache**
 * and each call of {@link #parse(String)} or {@link #get(UUID)} will update the usage-timestamp for
 * the component ID.
 * <p>
 * Users can react to eviction by adding a listener to
 * {@link #addComponentIdRemovedListener(Consumer)}.
 * <p>
 * The store is fully thread-safe, component IDs can be generated and parsed multi-threaded.
 */
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
    /**
     * In-memory cache which is used as first stage before the database, to speedup look-ups.
     * Usually covers about 95% of all queries.
     */
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

    /**
     * Creates a new instance with default eviction settings.
     *
     * @param database the database to use to persist component IDs in
     */
    public ComponentIdStore(@NotNull Database database) {
        this(database, EVICT_EVERY_INITIAL_DELAY, EVICT_EVERY_DELAY, EVICT_EVERY_UNIT,
                EVICT_OLDER_THAN, EVICT_OLDER_THAN_UNIT);
    }

    /**
     * Creates a new instance with given eviction settings.
     *
     * @param database the database to use to persist component IDs in
     * @param evictEveryInitialDelay delay before the first eviction is triggered
     * @param evictEveryDelay delay after which the next eviction is triggered, measured after an
     *        eviction is done
     * @param evictEveryUnit the unit of the 'evictEvery' values
     * @param evictOlderThan component IDs that have not been used for longer than this will be
     *        deleted during eviction
     * @param evictOlderThanUnit the unit of the 'evictOlderThan' value
     */
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

    /**
     * Adds a listener for component ID removal. The listener is triggered during eviction, once for
     * each component ID that has been removed from the store.
     * <p>
     * The listener might be triggered multi-threaded, there are no guarantees made regarding the
     * executing thread. In particular, it might be a different thread each time it is triggered.
     *
     * @param listener the listener to add
     */
    public void addComponentIdRemovedListener(@NotNull Consumer<ComponentId> listener) {
        componentIdRemovedListeners.add(listener);
    }

    /**
     * Gets the component ID associated to the given UUID.
     * <p>
     * If a component ID is not present, this can either mean it was never inserted before or it has
     * been evicted already. Use {@link #addComponentIdRemovedListener(Consumer)} to react to this
     * event.
     * <p>
     * Use {@link #generate(ComponentId, Lifespan)} or
     * {@link #putOrThrow(UUID, ComponentId, Lifespan)} to add component IDs.
     *
     * @param uuid the UUID to lookup
     * @return the associated component ID, if present
     * @throws InvalidComponentIdFormatException if the given component ID was in an unexpected
     *         format and could not be serialized
     */
    public @NotNull Optional<ComponentId> get(@NotNull UUID uuid) {
        Optional<ComponentId> componentId =
                Optional.ofNullable(uuidToComponentId.get(uuid)).or(() -> getFromDatabase(uuid));

        if (componentId.isPresent()) {
            heatService.execute(() -> heatRecord(uuid));
        }

        return componentId;
    }

    /**
     * Adds the given component ID to the store, associated with the given UUID as key.
     * <p>
     * The method throws if the UUID is already associated to a component ID. After a component ID
     * has been evicted (see {@link #addComponentIdRemovedListener(Consumer)}), it is safe to call
     * this method again for the evicted UUID.
     *
     * @param uuid the UUID to associate the component ID with
     * @param componentId the component ID to add to the store
     * @param lifespan the lifespan of the component ID, controls when it will be targeted for
     *        eviction
     * @throws IllegalArgumentException if the given UUID is already associated to a component ID
     * @throws InvalidComponentIdFormatException if the component ID associated to the given UUID
     *         was in an unexpected format and could not be deserialized
     */
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

    /**
     * Updates the <b>last_used</b> timestamp for the given UUID in the database to the current
     * time. This effectively heats the record, so that it will not be targeted for the next
     * evictions.
     *
     * @param uuid the uuid to heat
     * @throws IllegalArgumentException if there is no, or multiple, records associated to that UUID
     */
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
