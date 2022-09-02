package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.togetherjava.tjbot.features.componentids.ComponentId;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.Lifespan;

import java.util.List;

/**
 * Represents a Discord slash-command. Mostly decorating
 * {@link net.dv8tion.jda.api.interactions.commands.Command}.
 * <p>
 * All slash commands have to implement this interface. For convenience, there is a
 * {@link SlashCommandAdapter} available that implemented most methods already. A new command can
 * then be registered by adding it to {@link Features}.
 * <p>
 * <p>
 * Slash commands can either be visible globally in Discord or just to specific guilds. They can
 * have subcommands, options, menus and more. This can be configured via {@link CommandData}, which
 * is then to be returned by {@link #getData()} where the system will then pick it up from.
 * <p>
 * After registration, the system will notify a command whenever one of its corresponding slash
 * commands ({@link #onSlashCommand(SlashCommandInteractionEvent)}), buttons
 * ({@link #onButtonClick(ButtonInteractionEvent, List)}) or menus
 * ({@link #onSelectionMenu(SelectMenuInteractionEvent, List)}) have been triggered.
 * <p>
 * <p>
 * Some example commands are available in {@link org.togetherjava.tjbot.features.basic}.
 */
public interface SlashCommand extends UserInteractor {

    /**
     * Gets the description of the command.
     * <p>
     * Requirements for this are documented in {@link Commands#slash(String, String)}.
     * <p>
     * <p>
     * After registration of the command, the description must not change anymore.
     *
     * @return the description of the command
     */
    String getDescription();

    /**
     * Gets the visibility of this command.
     * <p>
     * After registration of the command, the visibility must not change anymore.
     *
     * @return the visibility of the command
     */
    SlashCommandVisibility getVisibility();

    /**
     * Gets the command data belonging to this command.
     * <p>
     * The data can be used to configure the settings for this command, i.e. adding options,
     * subcommands, menus and more.
     * <p>
     * See {@link CommandData} for details on how to create and configure instances of it.
     * <p>
     * <p>
     * This method may be called multiple times, implementations must not create new data each time
     * but instead configure it once beforehand. The core system will automatically call this method
     * to register the command to Discord.
     *
     * @return the command data of this command
     */
    SlashCommandData getData();

    /**
     * Triggered by the core system when a slash command corresponding to this implementation (based
     * on {@link #getData()}) has been triggered.
     * <p>
     * This method may be called multi-threaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it.
     * <p>
     * Buttons or menus have to be created with a component ID (see
     * {@link ComponentInteraction#getComponentId()},
     * {@link net.dv8tion.jda.api.interactions.components.buttons.Button#of(ButtonStyle, String, Emoji)})
     * in a very specific format, otherwise the core system will fail to identify the command that
     * corresponded to the button or menu click event and is unable to route it back.
     * <p>
     * The component ID has to be a UUID-string (see {@link java.util.UUID}), which is associated to
     * a specific database entry, containing meta information about the command being executed. Such
     * a database entry can be created and a UUID be obtained by using
     * {@link ComponentIdGenerator#generate(ComponentId, Lifespan)}, as provided by the instance
     * given to {@link #acceptComponentIdGenerator(ComponentIdGenerator)} during system setup. The
     * required {@link ComponentId} instance accepts optional extra arguments, which, if provided,
     * can be picked up during the corresponding event (see
     * {@link #onButtonClick(ButtonInteractionEvent, List)},
     * {@link #onSelectionMenu(SelectMenuInteractionEvent, List)}).
     * <p>
     * Alternatively, if {@link SlashCommandAdapter} has been extended, it also offers a handy
     * {@link SlashCommandAdapter#generateComponentId(String...)} method to ease the flow.
     * <p>
     * See <a href="https://github.com/Together-Java/TJ-Bot/wiki/Component-IDs">Component-IDs</a> on
     * our Wiki for more details and examples of how to use component IDs.
     * <p>
     * This method will be called in a multi-threaded context and the event may not be hold valid
     * forever.
     *
     * @param event the event that triggered this
     */
    void onSlashCommand(SlashCommandInteractionEvent event);
}
