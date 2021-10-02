package org.togetherjava.tjbot.commands.system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ComponentId {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);
    private final int id;
    private final String commandName;
    private final List<String> elements;

    public ComponentId(@NotNull String commandName, @NotNull List<String> elements) {
        this(NEXT_ID.getAndIncrement(), commandName, elements);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private ComponentId(@JsonProperty("id") int id,
            @JsonProperty("commandName") @NotNull String commandName,
            @JsonProperty("elements") @NotNull List<String> elements) {
        this.id = id;
        this.commandName = commandName;
        this.elements = elements;
    }

    public @NotNull String getCommandName() {
        return commandName;
    }

    public int getId() {
        return id;
    }

    public @NotNull List<String> getElements() {
        return Collections.unmodifiableList(elements);
    }
}
