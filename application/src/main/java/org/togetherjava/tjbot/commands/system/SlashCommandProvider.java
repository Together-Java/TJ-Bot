package org.togetherjava.tjbot.commands.system;

import org.togetherjava.tjbot.commands.SlashCommand;

import javax.annotation.Nonnull;
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
    @Nonnull
    Collection<SlashCommand> getSlashCommands();

    /**
     * Gets the slash command registered under the given name, if any.
     *
     * @param name the name of the command
     * @return the command registered under this name, if any
     */
    @Nonnull
    Optional<SlashCommand> getSlashCommand(String name);
}
