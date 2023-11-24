package org.togetherjava.tjbot.features.system;

import org.togetherjava.tjbot.features.BotCommand;
import org.togetherjava.tjbot.features.UserInteractor;

import java.util.Collection;

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
}
