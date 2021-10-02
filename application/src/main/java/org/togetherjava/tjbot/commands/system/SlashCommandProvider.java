package org.togetherjava.tjbot.commands.system;

import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommand;

import java.util.Collection;
import java.util.Optional;

public interface SlashCommandProvider {
    @NotNull
    Collection<SlashCommand> getSlashCommands();

    @NotNull
    Optional<SlashCommand> getSlashCommand(@NotNull String name);
}
