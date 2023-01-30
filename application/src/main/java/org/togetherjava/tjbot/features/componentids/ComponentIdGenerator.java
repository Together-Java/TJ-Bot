package org.togetherjava.tjbot.features.componentids;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import org.togetherjava.tjbot.features.SlashCommand;

/**
 * Provides component ID generation.
 * <p>
 * Component IDs are used during button or selection menu events. They can carry arbitrary data and
 * are persisted by the system.
 * <p>
 * See {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)} for more context on how to
 * use this.
 * <p>
 * The interface {@link ComponentIdParser} is the counterpart to this, offering parsing back the
 * payload from the ID.
 */
@FunctionalInterface
public interface ComponentIdGenerator {
    /**
     * Generates and persists a valid component ID for the given payload, which can then be used in
     * interactions, such as button or selection menus.
     * <p>
     * See {@link ComponentInteraction#getComponentId()} and
     * {@link Button#of(ButtonStyle, String, String)} for details on where the generated ID can be
     * used.
     *
     * @param componentId the component ID payload to persist and generate a valid ID for
     * @param lifespan the lifespan of the generated and persisted component ID
     * @return a UUID for the given payload, which can be used as component ID
     * @throws InvalidComponentIdFormatException if the given component ID was in an unexpected
     *         format and could not be serialized
     */
    String generate(ComponentId componentId, Lifespan lifespan);
}
