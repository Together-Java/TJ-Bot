package org.togetherjava.tjbot.commands.system;

import org.togetherjava.tjbot.commands.UserInteractor;

import javax.annotation.Nullable;
import java.util.Collection;
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
     * Gets the slash command registered under the given name, if any.
     *
     * @param name the name of the command (including its prefix)
     * @return the command registered under this name, if any
     */
    public Optional<UserInteractor> getInteractor(String name);

    /**
     * Gets the slash command registered under the given name, if any.
     *
     * @param name the name of the command
     * @param type the type of the command
     * @return the command registered under this name, if any
     */
    public <T extends UserInteractor, C extends Class<T>> Optional<T> getInteractor(String name,
            @Nullable C type);
}
