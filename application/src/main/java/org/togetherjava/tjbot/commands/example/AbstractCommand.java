package org.togetherjava.tjbot.commands.example;

import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.Command;

/**
 * Makes usage of constructor to store the {@link #commandName}, {@link #description} and the
 * {@link #isGuildOnly} <br>
 * This helps against a tiny bit of duplicated code.
 */
public class AbstractCommand implements Command {
    private final String commandName;
    private final String description;
    private final boolean isGuildOnly;

    public AbstractCommand(String commandName, String description, boolean isGuildOnly) {
        this.commandName = commandName;
        this.description = description;
        this.isGuildOnly = isGuildOnly;
    }

    public AbstractCommand(String commandName, String description) {
        this(commandName, description, false);
    }

    @Override
    public @NotNull String getCommandName() {
        return commandName;
    }

    @Override
    public @NotNull String getDescription() {
        return description;
    }

    @Override
    public boolean isGuildOnly() {
        return isGuildOnly;
    }
}
