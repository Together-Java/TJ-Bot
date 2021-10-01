package org.togetherjava.tjbot.commands.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Utility class to generate component IDs valid per
 * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)}.
 * <p>
 * <p>
 * There is also the option to extend {@link org.togetherjava.tjbot.commands.SlashCommandAdapter}
 * and use its
 * {@link org.togetherjava.tjbot.commands.SlashCommandAdapter#generateComponentId(String...)}
 * method.
 */
public enum ComponentIds {
    ;

    private static final CsvMapper CSV = new CsvMapper();

    /**
     * Generates a component ID valid per
     * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)}.
     *
     * @param commandName the name of the command that corresponds to this component ID
     * @param elements the additional elements to transport with the component, can be empty
     * @return the generated component ID
     * @throws JsonProcessingException if generation failed
     */
    public static @NotNull String generate(@NotNull String commandName,
            @NotNull List<String> elements) throws JsonProcessingException {
        return CSV.writerFor(ComponentId.class)
            .with(CSV.schemaFor(ComponentId.class))
            .writeValueAsString(new ComponentId(commandName, elements));
    }

    /**
     * Parses component IDs from their text form.
     *
     * @param componentId the component ID to parse, must be valid per
     *        {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(SlashCommandEvent)}
     * @return the parsed component ID object
     * @throws JsonProcessingException if parsing failed, for example because the component ID was
     *         not in a valid format
     */
    public static @NotNull ComponentId parse(@NotNull String componentId)
            throws JsonProcessingException {
        return CSV.readerFor(ComponentId.class)
            .with(CSV.schemaFor(ComponentId.class))
            .readValue(componentId);
    }
}
