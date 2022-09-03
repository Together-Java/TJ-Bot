package org.togetherjava.tjbot.commands.system;

import org.togetherjava.tjbot.commands.BotCommand;

import java.util.Collection;
import java.util.Optional;

/**
 * Provides all registered commands.
 */
public interface CommandProvider {
    /**
     * Gets a list of all currently available and registered commands.
     *
     * @return all commands
     */
    Collection<BotCommand> getBotCommands();

    /**
     * Gets the slash command registered under the given name, if any.
     *
     * @param name the name of the command
     * @param type the type of the command
     * @return the command registered under this name, if any
     */
    public <T extends BotCommand, C extends Class<T>> Optional<T> getCommand(String name, C type);
}