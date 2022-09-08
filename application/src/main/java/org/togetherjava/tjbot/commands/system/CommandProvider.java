package org.togetherjava.tjbot.commands.system;

import org.togetherjava.tjbot.commands.BotCommand;
import org.togetherjava.tjbot.commands.UserInteractor;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provides all registered commands.
 */
public interface CommandProvider {
    /**
     * Gets a list of all currently available and registered interactors.
     *
     * @return all interactors
     */
    Collection<UserInteractor> getInteractors();

    /**
     * Gets a list of all currently available and registered interactors.
     *
     * @return all interactors
     */
    default Collection<BotCommand> getCommands() {
        return getInteractors()
                .stream()
                .filter(BotCommand.class::isInstance)
                .map(BotCommand.class::cast)
                .toList();
    }

    /**
     * Gets the slash command registered under the given name, if any.
     *
     * @param name the name of the command (including its prefix)
     * @return the command registered under this name, if any
     */
    Optional<UserInteractor> getInteractor(String name);

    /**
     * Gets the slash command registered under the given name, if any.
     *
     * @param name the name of the command
     * @param type the type of the command
     * @return the command registered under this name, if any
     */
    <T extends UserInteractor> Optional<T> getInteractor(String name,
                                                                @Nullable Class<? extends T> type);

    /**
     * Gets any interactors with the given name.
     * The name you give should be without prefix, these methods checks all prefixes themselves.
     * If your name is with prefix, use {@link #getInteractor(String)}
     *
     * @param name the name of the command
     * @return a list of commands with that name
     */
    List<UserInteractor> getAnyInteractors(final String name);
}