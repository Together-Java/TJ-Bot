package org.togetherjava.tjbot.commands.componentids;

import org.jetbrains.annotations.NotNull;

public interface ComponentIdGenerator {
    @NotNull
    String generate(@NotNull ComponentId componentId, @NotNull Lifespan lifespan);
}
