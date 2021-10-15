package org.togetherjava.tjbot.commands.componentids;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for component IDs, see
 * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)} for its
 * usages.
 */
public final class ComponentId {
    private final String commandName;
    private final List<String> elements;

    /**
     * Creates a new component ID with the given data.
     *
     * @param commandName the name of the command that corresponds to this component ID
     * @param elements the extra elements contained in this component ID, may be empty
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ComponentId(@JsonProperty("commandName") @NotNull String commandName,
            @JsonProperty("elements") @NotNull List<String> elements) {
        this.commandName = commandName;
        this.elements = new ArrayList<>(elements);
    }

    /**
     * Gets the name of the command that corresponds to this component ID.
     *
     * @return the name of the command
     */
    public @NotNull String getCommandName() {
        return commandName;
    }

    /**
     * Gets the extra elements contained in this component ID
     *
     * @return the extra elements
     */
    public @NotNull List<String> getElements() {
        return Collections.unmodifiableList(elements);
    }
}
