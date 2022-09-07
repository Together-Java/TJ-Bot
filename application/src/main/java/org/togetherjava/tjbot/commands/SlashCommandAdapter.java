package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Adapter implementation of a {@link SlashCommand}. The minimal setup only requires implementation
 * of {@link #onSlashCommand(SlashCommandInteractionEvent)}. A new command can then be registered by
 * adding it to {@link Features}.
 * <p>
 * Further, {@link #onButtonClick(ButtonInteractionEvent, List)} and
 * {@link #onSelectionMenu(SelectMenuInteractionEvent, List)} can be overridden if desired. The
 * default implementation is empty, the adapter will not react to such events.
 * <p>
 * <p>
 * The adapter manages all command related data itself, which can be provided during construction
 * (see {@link #SlashCommandAdapter(String, String, CommandVisibility)}). In order to add options,
 * subcommands or similar command configurations, use {@link #getData()} and mutate the returned
 * data object (see {@link CommandData} for details on how to work with this class).
 * <p>
 * <p>
 * If implementations want to add buttons or selection menus, it is highly advised to use component
 * IDs generated by {@link #generateComponentId(String...)}, which will automatically create IDs
 * that are valid per {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
 * <p>
 * <p>
 * Some example commands are available in {@link org.togetherjava.tjbot.commands.basic}. A minimal
 * setup would consist of a class like
 *
 * <pre>
 * {
 *     &#64;code
 *     public class PingCommand extends SlashCommandAdapter {
 *         public PingCommand() {
 *             super("ping", "Responds with 'Pong!'", SlashCommandVisibility.GUILD);
 *         }
 *
 *         &#64;Override
 *         public void onSlashCommand(SlashCommandInteractionEvent event) {
 *             event.reply("Pong!").queue();
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * and registration of an instance of that class in {@link Features}.
 */
public abstract class SlashCommandAdapter extends BotCommandAdapter implements SlashCommand {
    private final String description;
    private final SlashCommandData data;
    private ComponentIdGenerator componentIdGenerator;

    /**
     * Creates a new adapter with the given data.
     *
     * @param name the name of this command, requirements for this are documented in
     *        {@link SlashCommandData#setName(String)}
     * @param description the description of this command, requirements for this are documented in
     *        {@link SlashCommandData#setDescription(String)}
     * @param visibility the visibility of the command
     */
    protected SlashCommandAdapter(String name, String description, CommandVisibility visibility) {
        super(Commands.slash(name, description), visibility);
        this.description = description;

        this.data = getData();
    }

    @Override
    public final String getDescription() {
        return description;
    }

    @Override
    public final @NotNull SlashCommandData getData() {
        return data;
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onAutoComplete(@NotNull CommandAutoCompleteInteractionEvent event) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    /**
     * Copies the given option multiple times.
     * <p>
     * The generated options are all not required (optional) and have ascending number suffixes on
     * their name. For example, if the name of the given option is {@code "foo"}, calling this with
     * an amount of {@code 5} would result in a list of options like:
     * <ul>
     * <li>{@code "foo1"}</li>
     * <li>{@code "foo2"}</li>
     * <li>{@code "foo3"}</li>
     * <li>{@code "foo4"}</li>
     * <li>{@code "foo5"}</li>
     * </ul>
     * <p>
     * This can be useful to offer a variable amount of input options for a user, similar to
     * <i>varargs</i>.
     * <p>
     * After generation, the user input can conveniently be parsed back using
     * {@link #getMultipleOptionsByNamePrefix(CommandInteractionPayload, String)}.
     *
     * @param optionData the original option to copy
     * @param amount how often to copy the option
     * @return the generated list of options
     */
    @Unmodifiable
    protected static List<OptionData> generateMultipleOptions(OptionData optionData,
            @Range(from = 1, to = 25) int amount) {
        String baseName = optionData.getName();

        Function<String, OptionData> nameToOption =
                name -> new OptionData(optionData.getType(), name, optionData.getDescription());

        return IntStream.rangeClosed(1, amount)
            .mapToObj(i -> baseName + i)
            .map(nameToOption)
            .toList();
    }

    /**
     * Gets all options from the given event whose name start with the given prefix.
     *
     * @param event the event to extract options from
     * @param namePrefix the name prefix to search for
     * @return all options with the given prefix
     */
    @Unmodifiable
    protected static List<OptionMapping> getMultipleOptionsByNamePrefix(
            CommandInteractionPayload event, String namePrefix) {
        return event.getOptions()
            .stream()
            .filter(option -> option.getName().startsWith(namePrefix))
            .toList();
    }
}