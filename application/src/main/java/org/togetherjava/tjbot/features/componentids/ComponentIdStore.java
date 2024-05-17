package org.togetherjava.tjbot.features.componentids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ComponentIds;
import org.togetherjava.tjbot.db.generated.tables.records.ComponentIdsRecord;
import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.logging.LogMarkers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Thread-safe storage for component IDs. Can put, persist and get back component IDs based on
 * UUIDs. Component IDs are used for button and selection menu commands, see
 * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)} for details.
 * <p>
 * Use {@link #putOrThrow(UUID, ComponentId, Lifespan)} to put and persist a component ID; and
 * {@link #get(UUID)} to get it back. Component IDs are persisted during application runs and can
 * hence be retrieved back even after long times.
 * <p>
 * <p>
 * Component IDs which have not been used for a long time, depending on their {@link Lifespan}
 * setting, might get evicted from the store after some time. The store implements a
 * <strong>LRU-cache</strong> and each call of {@link #get(UUID)} will update the usage-timestamp
 * for the component ID.
 * <p>
 * Users can react to eviction by adding a listener to
 * {@link #addComponentIdRemovedListener(Consumer)}.
 * <p>
 * The store is fully thread-safe, component IDs can be generated and parsed multithreaded.
 */
@SuppressWarnings("ClassWithTooManyFields")
public final class ComponentIdStore implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ComponentIdStore.class);
    private static final CsvMapper CSV = new CsvMapper();

    private static final long EVICT_DATABASE_EVERY_INITIAL_DELAY = 1;
    private static final long EVICT_DATABASE_EVERY_DELAY = 15;
    private static final ChronoUnit EVICT_DATABASE_EVERY_UNIT = ChronoUnit.MINUTES;
    private static final long EVICT_DATABASE_OLDER_THAN = 20;
    private static final ChronoUnit EVICT_DATABASE_OLDER_THAN_UNIT = ChronoUnit.DAYS;

    private static final int CACHE_SIZE = 1_000;
    private static final int EVICT_CACHE_OLDER_THAN = 2;
    private static final ChronoUnit EVICT_CACHE_OLDER_THAN_UNIT = ChronoUnit.HOURS;

    private final Object storeLock = new Object();
    private final Database database;
    /**
     * In-memory cache which is used as first stage before the database, to speedup look-ups. Should
     * cover the majority of all queries, as most queries (e.g. button clicks) come from messages
     * that have been created in the past hours and not days.
     */
    private final Cache<UUID, ComponentId> storeCache;
    private final Collection<Consumer<ComponentId>> componentIdRemovedListeners =
            Collections.synchronizedCollection(new ArrayList<>());
    private final ExecutorService heatService = Executors.newCachedThreadPool();
    private final ExecutorService componentIdRemovedListenerService =
            Executors.newCachedThreadPool();
    private final ScheduledExecutorService evictionService =
            Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> evictionTask;
    private final long evictDatabaseOlderThan;
    private final TemporalUnit evictDatabaseOlderThanUnit;

    /**
     * Creates a new instance with default eviction settings.
     *
     * @param database the database to use to persist component IDs in
     */
    public ComponentIdStore(Database database) {
        this(database, EVICT_DATABASE_EVERY_INITIAL_DELAY, EVICT_DATABASE_EVERY_DELAY,
                EVICT_DATABASE_EVERY_UNIT, EVICT_DATABASE_OLDER_THAN,
                EVICT_DATABASE_OLDER_THAN_UNIT);
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
    @SuppressWarnings({"WeakerAccess", "ConstructorWithTooManyParameters"})
    public ComponentIdStore(Database database, long evictEveryInitialDelay, long evictEveryDelay,
            ChronoUnit evictEveryUnit, long evictOlderThan, ChronoUnit evictOlderThanUnit) {
        this.database = database;
        evictDatabaseOlderThan = evictOlderThan;
        evictDatabaseOlderThanUnit = evictOlderThanUnit;

        storeCache = Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .expireAfterAccess(EVICT_CACHE_OLDER_THAN, TimeUnit.of(EVICT_CACHE_OLDER_THAN_UNIT))
            .build();

        Runnable evictCommand = () -> {
            try {
                evictDatabase();
            } catch (Exception e) {
                logger.error("Unknown error while evicting the component ID store database.", e);
            }
        };
        evictionTask = evictionService.scheduleWithFixedDelay(evictCommand, evictEveryInitialDelay,
                evictEveryDelay, TimeUnit.of(evictEveryUnit));

        logDebugSizeStatistics();
    }

    /**
     * Adds a listener for component ID removal. The listener is triggered during eviction, once for
     * each component ID that has been removed from the store.
     * <p>
     * The listener might be triggered multithreaded, there are no guarantees made regarding the
     * executing thread. In particular, it might be a different thread each time it is triggered.
     *
     * @param listener the listener to add
     */
    public void addComponentIdRemovedListener(Consumer<ComponentId> listener) {
        componentIdRemovedListeners.add(listener);
    }

    /**
     * Gets the component ID associated to the given UUID.
     * <p>
     * If a component ID is not present, this can either mean it was never inserted before or it has
     * been evicted already. Use {@link #addComponentIdRemovedListener(Consumer)} to react to this
     * event.
     * <p>
     * Use {@link #putOrThrow(UUID, ComponentId, Lifespan)} to add component IDs.
     *
     * @param uuid the UUID to lookup
     * @return the associated component ID, if present
     * @throws InvalidComponentIdFormatException if the given component ID was in an unexpected
     *         format and could not be serialized
     */
    @SuppressWarnings("WeakerAccess")
    public Optional<ComponentId> get(UUID uuid) {
        synchronized (storeLock) {
            // Get it from the cache or, if not found, the database
            return Optional.ofNullable(storeCache.getIfPresent(uuid)).or(() -> {
                Optional<ComponentId> databaseComponentId = getFromDatabase(uuid);
                databaseComponentId.ifPresent(id -> {
                    // Put it back into the cache
                    storeCache.put(uuid, id);

                    heatService.execute(() -> heatRecord(uuid));
                });
                return databaseComponentId;
            });
        }
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
    @SuppressWarnings("WeakerAccess")
    public void putOrThrow(UUID uuid, ComponentId componentId, Lifespan lifespan) {
        Supplier<String> alreadyExistsMessageSupplier =
                () -> "The UUID '%s' already exists and is associated to a component id."
                    .formatted(uuid);

        synchronized (storeLock) {
            if (storeCache.getIfPresent(uuid) != null) {
                throw new IllegalArgumentException(alreadyExistsMessageSupplier.get());
            }
            storeCache.put(uuid, componentId);

            database.writeTransaction(context -> {
                String uuidText = uuid.toString();
                if (context.fetchExists(ComponentIds.COMPONENT_IDS,
                        ComponentIds.COMPONENT_IDS.UUID.eq(uuidText))) {
                    throw new IllegalArgumentException(alreadyExistsMessageSupplier.get());
                }

                ComponentIdsRecord componentIdsRecord =
                        context.newRecord(ComponentIds.COMPONENT_IDS)
                            .setUuid(uuid.toString())
                            .setComponentId(serializeComponentId(componentId))
                            .setLastUsed(Instant.now())
                            .setLifespan(lifespan.name());
                componentIdsRecord.insert();
            });
        }
    }

    private Optional<ComponentId> getFromDatabase(UUID uuid) {
        return database.read(context -> Optional
            .ofNullable(context.selectFrom(ComponentIds.COMPONENT_IDS)
                .where(ComponentIds.COMPONENT_IDS.UUID.eq(uuid.toString()))
                .fetchOne())
            .map(ComponentIdsRecord::getComponentId)
            .map(ComponentIdStore::deserializeComponentId));
    }

    /**
     * Updates the <b>last_used</b> timestamp for the given UUID in the database to the current
     * time. This effectively heats the record, so that it will not be targeted for the next
     * evictions.
     *
     * @param uuid the uuid to heat
     * @throws IllegalArgumentException if there is no, or multiple, records associated to that UUID
     */
    private void heatRecord(UUID uuid) {
        int updatedRecords;
        synchronized (storeLock) {
            updatedRecords =
                    database.writeAndProvide(context -> context.update(ComponentIds.COMPONENT_IDS)
                        .set(ComponentIds.COMPONENT_IDS.LAST_USED, Instant.now())
                        .where(ComponentIds.COMPONENT_IDS.UUID.eq(uuid.toString()))
                        .execute());
        }

        // NOTE Case 0, where no records are updated, is ignored on purpose.
        // This happens when the entry has been evicted before the heating was executed.
        if (updatedRecords > 1) {
            throw new AssertionError(
                    "Multiple records had the UUID '%s' even though it is unique.".formatted(uuid));
        }
    }

    private void evictDatabase() {
        logger.debug("Evicting old non-permanent component ids from the database...");
        AtomicInteger evictedCounter = new AtomicInteger(0);
        synchronized (storeLock) {
            database.write(context -> {
                Result<ComponentIdsRecord> oldRecords = context
                    .selectFrom(ComponentIds.COMPONENT_IDS)
                    .where(ComponentIds.COMPONENT_IDS.LIFESPAN.notEqual(Lifespan.PERMANENT.name())
                        .and(ComponentIds.COMPONENT_IDS.LAST_USED.lessOrEqual(Instant.now()
                            .minus(evictDatabaseOlderThan, evictDatabaseOlderThanUnit))))
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
                            "Evicted component id with uuid '{}' from user interactor '{}', last used '{}'",
                            uuid, componentId.userInteractorName(), lastUsed);

                    // Remove them from the cache if still in there
                    storeCache.invalidate(uuid);
                    // Notify all listeners, but non-blocking to not delay eviction
                    componentIdRemovedListeners
                        .forEach(listener -> componentIdRemovedListenerService
                            .execute(() -> listener.accept(componentId)));
                });
            });
        }

        if (evictedCounter.get() != 0) {
            logger.info("Evicted {} old non-permanent component ids from the database",
                    evictedCounter.get());
        }
    }

    private static String serializeComponentId(ComponentId componentId) {
        try {
            return CSV.writerFor(ComponentId.class)
                .with(CSV.schemaFor(ComponentId.class))
                .writeValueAsString(componentId);
        } catch (JsonProcessingException e) {
            throw new InvalidComponentIdFormatException(e);
        }
    }

    private static ComponentId deserializeComponentId(String componentId) {
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

        Map<Lifespan, Integer> lifespanToCount = Arrays.stream(Lifespan.values())
            .collect(Collectors.toMap(Function.identity(),
                    lifespan -> database
                        .read(context -> context.fetchCount(ComponentIds.COMPONENT_IDS,
                                ComponentIds.COMPONENT_IDS.LIFESPAN.eq(lifespan.name())))));
        int recordsCount = lifespanToCount.values().stream().mapToInt(Integer::intValue).sum();

        logger.debug(LogMarkers.SENSITIVE, "The component id store consists of {} records ({})",
                recordsCount, lifespanToCount);
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
