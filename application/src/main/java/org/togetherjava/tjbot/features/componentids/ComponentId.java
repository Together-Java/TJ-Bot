package org.togetherjava.tjbot.features.componentids;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.List;

/**
 * Payload carried by component IDs. See
 * {@link org.togetherjava.tjbot.features.SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}
 * for its usages.
 *
 * @param userInteractorName the name of the user interactor that handles the event associated to
 *        this component ID, when triggered
 * @param elements the additional elements to carry along this component ID, empty if not desired
 */
public record ComponentId(String userInteractorName, List<String> elements) {
}
