package org.togetherjava.tjbot.commands.componentids;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

import java.util.List;

/**
 * Wrapper for component IDs, see
 * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)} for its
 * usages.
 */
public record ComponentId(String commandName, List<String> elements) {
}
