package org.togetherjava.tjbot.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.system.ComponentIds;

import java.util.Arrays;
import java.util.List;

/**
 * Adapter implementation of a {@link SlashCommand}. The minimal setup only requires implementation
 * of {@link #onSlashCommand(SlashCommandEvent)}. A new command can then be registered by adding it
 * to {@link Commands}.
 * <p>
 * Further, {@link #onButtonClick(ButtonClickEvent, List)} and
 * {@link #onSelectionMenu(SelectionMenuEvent, List)} can be overridden if desired. The default
 * implementation is empty, the adapter will not react to such events.
 * <p>
 * <p>
 * The adapter manages all command related data itself, which can be provided during construction
 * (see {@link #SlashCommandAdapter(String, String, SlashCommandVisibility)}. In order to add
 * options, subcommands or similar command configurations, use {@link #getData()} and mutate the
 * returned data object (see {@link CommandData} for details on how to work with this class).
 * <p>
 * <p>
 * If implementations want to add buttons or selection menus, it is highly advised to use component
 * IDs generated by {@link #generateComponentId(String...)}, which will automatically create IDs
 * that are valid per {@link SlashCommand#onSlashCommand(SlashCommandEvent)}.
 * <p>
 * <p>
 * Some example commands are available in {@link org.togetherjava.tjbot.commands.basic}. A minimal
 * setup would consist of a class like
 * 
 * <pre>
 * {
 *     &#64;code
 *     class PingCommand extends SlashCommandAdapter {
 *         PingCommand() {
 *             super("ping", "Responds with 'Pong!'", SlashCommandVisibility.GUILD);
 *         }
 *
 *         &#64;Override
 *         public void onSlashCommand(@NotNull SlashCommandEvent event) {
 *             event.reply("Pong!").queue();
 *         }
 *     }
 * }
 * </pre>
 * 
 * and registration of an instance of that class in {@link Commands}.
 */
public abstract class SlashCommandAdapter implements SlashCommand {
    private final String name;
    private final String description;
    private final SlashCommandVisibility visibility;
    private final CommandData data;

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

        data = new CommandData(name, description);
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
    public final @NotNull CommandData getData() {
        return data;
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    /**
     * Helper method to generate component IDs that are considered valid per
     * {@link SlashCommand#onSlashCommand(SlashCommandEvent)}.
     * <p>
     * They can be used to create buttons or selection menus and transport additional data
     * throughout the event (e.g. the user id who created the button dialog).
     * <p>
     * <p>
     * The arguments must not be too long. The system will fail if the generated component ID exceed
     * the character limit specified in {@link SlashCommand#onSlashCommand(SlashCommandEvent)}.
     *
     * @param args the extra arguments that should be part of the ID
     * @return the generated component ID
     */
    public final @NotNull String generateComponentId(@NotNull String... args) {
        try {
            return ComponentIds.generate(getName(), Arrays.asList(args));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
