package org.togetherjava.tjbot.commands.componentids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import org.apache.commons.collections4.map.LRUMap;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.db.Database;

import java.util.*;
import java.util.function.Consumer;

public final class ComponentIdStore implements ComponentIdGenerator, ComponentIdParser {
    private static final CsvMapper CSV = new CsvMapper();

    private final Database database;
    private final Map<UUID, ComponentId> uuidToComponentId =
            Collections.synchronizedMap(new LRUMap<>(1_000));
    private final Collection<Consumer<ComponentId>> componentIdRemovedListeners = new ArrayList<>();

    public ComponentIdStore(@NotNull Database database) {
        // TODO Add database deletion routine
        this.database = database;
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

    public Optional<ComponentId> get(@NotNull UUID uuid) {
        // TODO Add database
        return Optional.ofNullable(uuidToComponentId.get(uuid));
    }

    public void putOrThrow(@NotNull UUID uuid, @NotNull ComponentId componentId,
            @NotNull Lifespan lifespan) {
        // TODO Add database
        uuidToComponentId.merge(uuid, componentId, (oldValue, nextValue) -> {
            throw new IllegalArgumentException(
                    "The UUID '%s' already exists and is associated to a component id."
                        .formatted(uuid));
        });
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
}
