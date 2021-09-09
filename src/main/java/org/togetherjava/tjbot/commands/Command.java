package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.togetherjava.tjbot.commands.example.CommandExample;

/**
 * The class all commands have to abstract.
 * <br> Look at {@link CommandExample} for an example.
 * <br> A second example will come for sub-commands.
 *
 */
public interface Command {

    /**
     * <p> The command's name.
     *
     * <p> <b>Requirements!</b>
     *  <ul>
     *      <li>Lowercase</li>
     *      <li>Alphanumeric (with dash)</li>
     *      <li>1 to 32 characters long</li>
     *  </ul>
     * @return
     *  The commands name as a {@link String}
     */
    String getCommandName();

    /**
     * <p> The command's description.
     *
     * <p> <b>Requirements!</b>
     *  <ul>
     *      <li>1 to 100 characters long</li>
     *  </ul>
     * @return
     *  The commands description as a {@link String}
     */
    String getDescription();

    /**
     * Whenever the command is only for guilds, optional method.
     *
     * @return
     *  Whenever the command is only for guilds as a {@link Boolean}
     */
    default boolean isGuildOnly() {
        return false;
    }

    /**
     * This command will add all the options to the slash-command.
     * <br> Look at {@link CommandExample#addOptions(CommandData)} for an example.
     *
     * @param commandData
     *  The {@link CommandData} where the options need to be added.
     * @return
     *  The changed {@link CommandData}
     */
    default CommandData addOptions(CommandData commandData) {
        return commandData;
    }

    /**
     * The execute method for when the command gets executed.
     * <br> Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#slash-commands">JDA's slash-commands Wiki article</a> for more information.
     *
     * @param event
     *  The relating {@link SlashCommandEvent}
     */
    default void onSlashCommand(SlashCommandEvent event) {}

    /**
     * The execute method for when a button related to this command gets clicked.
     * <br> Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#buttons">JDA's buttons Wiki article</a> for more information.
     *
     * @param event
     *  The reating {@link ButtonClickEvent}
     */
    default void onButtonClick(ButtonClickEvent event) {}

    /**
     * The execute method for when a selection menu related to this command gets clicked.
     * <br> JDA has no dedicated guide for selection menu's, they work the same as buttons.
     * <br> Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#buttons">JDA's buttons Wiki article</a> for more information.
     *
     * @param event
     *  The reating {@link ButtonClickEvent}
     */
    default void onSelectionMenu(SelectionMenuEvent event) {}

    // TODO: generateButtonId(String... args)? not sure
}
