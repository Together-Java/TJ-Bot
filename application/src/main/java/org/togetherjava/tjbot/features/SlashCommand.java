package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;

import java.util.List;

/**
 * A slash-command is a command in Discord, with slash (/) as the prefix. These commands offer
 * enhanced functionality and superior UX over text-commands. An example slash-command is the
 * `/thread` command, this allows you to create threads using your keyboard.
 *
 * <p>
 * Represents a Discord slash-command. Mostly decorating
 * {@link net.dv8tion.jda.api.interactions.commands.Command Command}.
 * <p>
 * All slash commands have to implement this interface. For convenience, there is a
 * {@link SlashCommandAdapter} available that implemented most methods already. A new command can
 * then be registered by adding it to {@link Features}.
 * <p>
 * Slash commands can either be visible globally in Discord or just to specific guilds. They can
 * have subcommands, options, menus and more. This can be configured via {@link CommandData}, which
 * is then to be returned by {@link #getData()} where the system will then pick it up from.
 * <p>
 * After registration, the system will notify a command whenever one of its corresponding slash
 * commands ({@link #onSlashCommand(SlashCommandInteractionEvent)}), buttons
 * ({@link #onButtonClick(ButtonInteractionEvent, List)}) or menus
 * ({@link #onEntitySelectSelection(EntitySelectInteractionEvent, List)},
 * {@link #onStringSelectSelection(StringSelectInteractionEvent, List)}) have been triggered.
 * <p>
 * Some example commands are available in {@link org.togetherjava.tjbot.features.basic}.
 */
public interface SlashCommand extends BotCommand {
    /**
     * Gets the description of the command.
     * <p>
     * Requirements for this are documented in {@link Commands#slash(String, String)}.
     * <p>
     * After registration of the command, the description must not change anymore.
     *
     * @return the description of the command
     */
    String getDescription();

    /**
     * Gets the command data belonging to this command.
     * <p>
     * The data can be used to configure the settings for this command, i.e. adding options,
     * subcommands, menus and more.
     * <p>
     * See {@link CommandData} for details on how to create and configure instances of it.
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
     * This method may be called multithreaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it.
     * <p>
     * Buttons or menus have to be created with a component ID (see
     * {@link ComponentInteraction#getComponentId()},
     * {@link net.dv8tion.jda.api.interactions.components.buttons.Button#of(ButtonStyle, String, String)})
     * in a very specific format, otherwise the core system will fail to identify the command that
     * corresponded to the button or menu click event and is unable to route it back.
     * <p>
     * See {@link #acceptComponentIdGenerator(ComponentIdGenerator)} for more info on the ID's.
     *
     * @param event the event that triggered this
     */
    void onSlashCommand(SlashCommandInteractionEvent event);

    /**
     * Autocompletion is comparable, but not the same as slash-command choices. Choices allow you to
     * set a static list of {@value OptionData#MAX_CHOICES} possible "choices" to the commmand.
     * Autocomplete allows you to dynamically give the user a list of
     * {@value OptionData#MAX_CHOICES} possible choices. These choices can be generated based on the
     * input of the user, the functionality is comparable to Google's autocompletion when searching
     * for something. The given choices are <b>not</b> enforced by Discord, the user can
     * <b>ignore</b> auto-completion and send whatever they want.
     * <p>
     * Triggered by the core system when a slash command's autocomplete corresponding to this
     * implementation (based on {@link #getData()}) has been triggered. Don't forget to enable
     * autocomplete using {@link OptionData#setAutoComplete(boolean)}!
     * <p>
     * This method may be called multithreaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it. <br>
     * See {@link #acceptComponentIdGenerator(ComponentIdGenerator)} for more info on the ID's.
     *
     * @param event the event that triggered this
     */
    void onAutoComplete(CommandAutoCompleteInteractionEvent event);
}
