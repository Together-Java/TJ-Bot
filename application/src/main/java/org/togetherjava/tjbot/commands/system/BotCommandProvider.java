package org.togetherjava.tjbot.commands.system;

import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.BotCommand;

import java.util.Collection;
import java.util.Optional;

/**
 * Provides all registered commands.
 */
public interface BotCommandProvider {
    /**
     * Gets a list of all currently available and registered commands.
     *
     * @return all slash commands
     */
    @NotNull
    Collection<BotCommand> getBotCommands();

    /**
     * Gets the command registered under the given name, if any.
     *
     * @param name the name of the command
     * @return the command registered under this name, if any
     */
    @NotNull
    Optional<BotCommand> getBotCommand(@NotNull String name);
}
