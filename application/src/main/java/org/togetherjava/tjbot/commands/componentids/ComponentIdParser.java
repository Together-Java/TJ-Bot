package org.togetherjava.tjbot.commands.componentids;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface ComponentIdParser {
    @NotNull
    Optional<ComponentId> parse(@NotNull String uuid);
}
