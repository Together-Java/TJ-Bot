package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;
import org.togetherjava.tjbot.commands.componentids.ComponentId;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.commands.componentids.Lifespan;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * (see {@link #SlashCommandAdapter(String, String, SlashCommandVisibility)}). In order to add
 * options, subcommands or similar command configurations, use {@link #getData()} and mutate the
 * returned data object (see {@link CommandData} for details on how to work with this class).
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
 *         public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
 *             event.reply("Pong!").queue();
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * and registration of an instance of that class in {@link Features}.
 */
public abstract class SlashCommandAdapter implements SlashCommand {
    private final String name;
    private final String description;
    private final SlashCommandVisibility visibility;
    private final SlashCommandData data;
    private ComponentIdGenerator componentIdGenerator;

    /**
     * Creates a new adapter with the given data.
     *
     * @param name the name of this command, requirements for this are documented in
     *        {@link CommandData#CommandData(String, String)}
     * @param description the description of this command, requirements for this are documented in
     *        {@link CommandData#CommandData(String, String)}
     * @param visibility the visibility of the command
     */
    protected SlashCommandAdapter(@NotNull String name, @NotNull String description,
            SlashCommandVisibility visibility) {
        this.name = name;
        this.description = description;
        this.visibility = visibility;

        data = Commands.slash(name, description);
    }

    @Override
    public final @NotNull String getName() {
        return name;
    }

    @Override
    public final @NotNull String getDescription() {
        return description;
    }

    @Override
    public final @NotNull SlashCommandVisibility getVisibility() {
        return visibility;
    }

    @Override
    public final @NotNull SlashCommandData getData() {
        return data;
    }

    @Override
    public final void acceptComponentIdGenerator(@NotNull ComponentIdGenerator generator) {
        componentIdGenerator = generator;
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onButtonClick(@NotNull ButtonInteractionEvent event, @NotNull List<String> args) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onSelectionMenu(@NotNull SelectMenuInteractionEvent event,
            @NotNull List<String> args) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    /**
     * Helper method to generate component IDs that are considered valid per
     * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
     * <p>
     * They can be used to create buttons or selection menus and transport additional data
     * throughout the event (e.g. the user id who created the button dialog).
     * <p>
     * IDs generated by this method have a regular lifespan, meaning that they might get evicted and
     * expire after not being used for a long time. Use
     * {@link #generateComponentId(Lifespan, String...)} to set other lifespans, if desired.
     *
     * @param args the extra arguments that should be part of the ID
     * @return the generated component ID
     */
    @SuppressWarnings("OverloadedVarargsMethod")
    protected final @NotNull String generateComponentId(@NotNull String... args) {
        return generateComponentId(Lifespan.REGULAR, args);
    }

    /**
     * Helper method to generate component IDs that are considered valid per
     * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
     * <p>
     * They can be used to create buttons or selection menus and transport additional data
     * throughout the event (e.g. the user id who created the button dialog).
     *
     * @param lifespan the lifespan of the component id, controls when an id that was not used for a
     *        long time might be evicted and expire
     * @param args the extra arguments that should be part of the ID
     * @return the generated component ID
     */
    @SuppressWarnings({"OverloadedVarargsMethod", "WeakerAccess"})
    protected final @NotNull String generateComponentId(@NotNull Lifespan lifespan,
            @NotNull String... args) {
        return Objects.requireNonNull(componentIdGenerator)
            .generate(new ComponentId(getName(), Arrays.asList(args)), lifespan);
    }

    /**
     * This method copies the given {@link OptionData} for the given amount of times into a
     * {@link List}. <br>
     * This makes all the {@link OptionData OptionData's} optional! Everything else gets exactly
     * copied.
     *
     * @param optionData The {@link OptionData} to copy.
     * @param amount The amount of times to copy
     *
     * @return An unmodifiable {@link List} of the copied {@link OptionData OptionData's}
     *
     * @see #varArgOptionsToList(Collection, Function)
     */
    @Unmodifiable
    protected static final @NotNull List<OptionData> generateOptionalVarArgList(
            final @NotNull OptionData optionData, @Range(from = 1, to = 25) final int amount) {

        OptionData varArgOption = new OptionData(optionData.getType(), optionData.getName(),
                optionData.getDescription());

        return IntStream.range(0, amount).mapToObj(i -> varArgOption).toList();
    }

    /**
     * This method takes a {@link Collection} of {@link OptionMapping OptionMapping's}, these get
     * mapped using the given {@link Function}
     *
     * @param options A {@link Collection} of {@link OptionMapping OptionMapping's}.
     * @param mapper The mapper {@link Function}
     * @param <T> The type to map it to.
     *
     * @return A modifiable {@link List} of the given type
     *
     * @see #generateOptionalVarArgList(OptionData, int)
     */
    protected static <T> List<T> varArgOptionsToList(
            final @NotNull Collection<? extends OptionMapping> options,
            final @NotNull Function<? super OptionMapping, ? extends T> mapper) {

        return options.stream().map(mapper).collect(Collectors.toCollection(ArrayList::new));
    }
}
