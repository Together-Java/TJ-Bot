package org.togetherjava.tjbot.commands.componentids;

import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Provides component ID parsing.
 * <p>
 * Component IDs are used during button or selection menu events. They can carry arbitrary data and
 * are persisted by the system.
 * <p>
 * See {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)} for
 * more context on how to use this.
 * <p>
 * The interface {@link ComponentIdGenerator} is the counter-part to this, offering generation of
 * IDs from payload.
 */
@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface ComponentIdParser {
    /**
     * Parses a previously generated and persisted component ID payload, as used during
     * interactions, such as button or selection menus.
     * <p>
     * See {@link ComponentInteraction#getComponentId()} and
     * {@link net.dv8tion.jda.api.interactions.components.Button#of(ButtonStyle, String, Emoji)} for
     * details on where the ID was originally transported with.
     *
     * @param uuid the UUID to parse which represents the component ID
     * @return the payload associated to the given UUID, if empty the component ID either never
     *         existed to begin with or expired due to its lifetime setting
     * @throws InvalidComponentIdFormatException if the component ID associated to the given UUID
     *         was in an unexpected format and could not be deserialized
     */
    @NotNull
    Optional<ComponentId> parse(@NotNull String uuid);
}
