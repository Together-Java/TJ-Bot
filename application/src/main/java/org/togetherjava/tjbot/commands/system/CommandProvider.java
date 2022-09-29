package org.togetherjava.tjbot.commands.system;

import org.togetherjava.tjbot.commands.BotCommand;
import org.togetherjava.tjbot.commands.UserInteractor;

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
     * Gets a list of all currently available and registered bot commands.
     *
     * @return all bot commands
     */
    default Collection<BotCommand> getCommands() {
        return getInteractors().stream()
            .filter(BotCommand.class::isInstance)
            .map(BotCommand.class::cast)
            .toList();
    }

    /**
     * Gets the interactor registered under the given name, if any.
     * <p>
     * This command excepts the name to include the prefix already, if you prefer using a
     * {@link Class} instance of the command/type instead, use {@link #getInteractor(String, Class)}
     * instead.-
     *
     * @param prefixedName the name of the command (including its prefix, see
     *        {@link org.togetherjava.tjbot.commands.UserInteractorPrefix UserInteractorPrefix}
     * @return the interactor registered under this name, if any
     */
    Optional<UserInteractor> getInteractor(String prefixedName);

    /**
     * Gets the interactor registered under the given name, if any.
     * <p>
     * Unlike {@link #getInteractor(String)}, this command expects the name, without the prefix (see
     * {@link org.togetherjava.tjbot.commands.UserInteractorPrefix UserInteractorPrefix}). Instead,
     * this command excepts a {@link Class} instance of the commands class. This can be
     * {@link org.togetherjava.tjbot.commands.SlashCommand SlashCommand}, or even as specific as
     * {@link org.togetherjava.tjbot.commands.basic.PingCommand PingCommand}.
     *
     * @param name the name of the command without prefix
     * @param type the type of the command
     * @return the interactor registered under this name, if any
     */
    <T extends UserInteractor> Optional<T> getInteractor(String name, Class<? extends T> type);

    /**
     * Gets any interactors with the given name. The name you give should be without prefix, these
     * methods checks all prefixes themselves. If your name is with prefix, use
     * {@link #getInteractor(String)}
     *
     * @param name the name of the command
     * @return a list of commands with that name, can be empty if none match
     */
    List<UserInteractor> getInteractorsWithName(final String name);
}