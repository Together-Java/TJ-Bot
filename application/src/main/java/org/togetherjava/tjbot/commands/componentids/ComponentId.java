package org.togetherjava.tjbot.commands.componentids;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Payload carried by component IDs. See
 * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)} for its
 * usages.
 *
 * @param commandName the name of the command that handles the event associated to this component
 *        ID, when triggered
 * @param elements the additional elements to carry along this component ID, empty if not desired
 */
public record ComponentId(@NotNull String commandName, @NotNull List<String> elements) {
}
