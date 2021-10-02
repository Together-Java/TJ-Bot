package org.togetherjava.tjbot.commands;

import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.basic.DatabaseCommand;
import org.togetherjava.tjbot.commands.basic.PingCommand;
import org.togetherjava.tjbot.db.Database;

import java.util.Collection;
import java.util.List;

public enum CommandRegistry {
    ;

    public static @NotNull Collection<SlashCommand> createSlashCommands(
            @NotNull Database database) {
        return List.of(new PingCommand(), new DatabaseCommand(database));
    }
}
