package org.togetherjava.tjbot.commands.componentids;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Payload carried by component IDs. See
 * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}
 * for its usages.
 *
 * @param userInteractorName the name of the user interactor that handles the event associated to
 *        this component ID, when triggered
 * @param elements the additional elements to carry along this component ID, empty if not desired
 */
public record ComponentId(@NotNull String userInteractorName, @NotNull List<String> elements) {
}
