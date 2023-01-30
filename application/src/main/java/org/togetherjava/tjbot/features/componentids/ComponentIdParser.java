package org.togetherjava.tjbot.features.componentids;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.util.Optional;

/**
 * Provides component ID parsing.
 * <p>
 * Component IDs are used during button or selection menu events. They can carry arbitrary data and
 * are persisted by the system.
 * <p>
 * See
 * {@link org.togetherjava.tjbot.features.SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}
 * for more context on how to use this.
 * <p>
 * The interface {@link ComponentIdGenerator} is the counterpart to this, offering generation of IDs
 * from payload.
 */
@FunctionalInterface
public interface ComponentIdParser {
    /**
     * Parses a previously generated and persisted component ID payload, as used during
     * interactions, such as button or selection menus.
     * <p>
     * See {@link ComponentInteraction#getComponentId()} and
     * {@link Button#of(ButtonStyle, String, String)} for details on where the ID was originally
     * transported with.
     *
     * @param uuid the UUID to parse which represents the component ID
     * @return the payload associated to the given UUID, if empty the component ID either never
     *         existed to begin with or expired due to its lifetime setting
     * @throws InvalidComponentIdFormatException if the component ID associated to the given UUID
     *         was in an unexpected format and could not be deserialized
     */
    Optional<ComponentId> parse(String uuid);
}
