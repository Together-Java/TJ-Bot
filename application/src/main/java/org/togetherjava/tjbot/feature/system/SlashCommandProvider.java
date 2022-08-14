package org.togetherjava.tjbot.feature.system;

import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.feature.SlashCommand;

import java.util.Collection;
import java.util.Optional;

/**
 * Provides all registered slash commands.
 */
public interface SlashCommandProvider {
    /**
     * Gets a list of all currently available and registered slash commands.
     *
     * @return all slash commands
     */
    @NotNull
    Collection<SlashCommand> getSlashCommands();

    /**
     * Gets the slash command registered under the given name, if any.
     *
     * @param name the name of the command
     * @return the command registered under this name, if any
     */
    @NotNull
    Optional<SlashCommand> getSlashCommand(@NotNull String name);
}
