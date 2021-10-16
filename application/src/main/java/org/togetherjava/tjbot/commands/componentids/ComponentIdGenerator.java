package org.togetherjava.tjbot.commands.componentids;

import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import org.jetbrains.annotations.NotNull;

/**
 * Provides component ID generation.
 * <p>
 * Component IDs are used during button or selection menu events. They can carry arbitrary data and
 * are persisted by the system.
 * <p>
 * See {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)} for
 * more context on how to use this.
 * <p>
 * The interface {@link ComponentIdParser} is the counter-part to this, offering parsing back the
 * payload from the ID.
 */
@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface ComponentIdGenerator {
    /**
     * Generates and persists a valid component ID for the given payload, which can then be used in
     * interactions, such as button or selection menus.
     * <p>
     * See {@link ComponentInteraction#getComponentId()} and
     * {@link net.dv8tion.jda.api.interactions.components.Button#of(ButtonStyle, String, Emoji)} for
     * details on where the generated ID can be used.
     *
     * @param componentId the component ID payload to persist and generate a valid ID for
     * @param lifespan the lifespan of the generated and persisted component ID
     * @return a UUID for the given payload, which can be used as component ID
     * @throws InvalidComponentIdFormatException if the given component ID was in an unexpected
     *         format and could not be serialized
     */
    @NotNull
    String generate(@NotNull ComponentId componentId, @NotNull Lifespan lifespan);
}
