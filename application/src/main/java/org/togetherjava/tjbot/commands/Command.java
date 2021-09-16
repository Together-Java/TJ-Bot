package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.Component;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.example.CommandExample;
import org.togetherjava.tjbot.commands.example.SubCommandExample;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The class all commands have to abstract. <br>
 * Instead of implementing this interface, you can also extend
 * {@link org.togetherjava.tjbot.commands.example.AbstractCommand}.
 *
 * Look at {@link CommandExample} for an example. <br>
 * Or look at {@link SubCommandExample} for an example with subcommands.
 *
 * <br>
 * <b> All commands that implement this interface need to be added command to the
 * {@link CommandHandler} class!</b>
 */
public interface Command {

    /**
     * <p>
     * The command's name.
     *
     * <p>
     * <b>Requirements!</b>
     * <ul>
     * <li>Lowercase</li>
     * <li>Alphanumeric (with dash)</li>
     * <li>1 to 32 characters long</li>
     * </ul>
     * 
     * @return The commands name as a {@link String}
     */
    @NotNull
    String getCommandName();

    /**
     * <p>
     * The command's description.
     *
     * <p>
     * <b>Requirements!</b>
     * <ul>
     * <li>1 to 100 characters long</li>
     * </ul>
     * 
     * @return The command's description as a {@link String}
     */

    @NotNull
    String getDescription();

    /**
     * Whenever the command is only for guilds, optional method.
     *
     * @return Whenever the command is only for guilds as a {@link Boolean}
     */
    default boolean isGuildOnly() {
        return false;
    }

    /**
     * This command will add all the options to the slash-command. <br>
     * Look at {@link CommandExample#addOptions(CommandData)} for an example.
     *
     * @param commandData The {@link CommandData} where the options need to be added.
     * @return The changed {@link CommandData}
     */
    default @NotNull CommandData addOptions(@NotNull CommandData commandData) {
        return commandData;
    }

    /**
     * The execute method for when the command gets executed. <br>
     * Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#slash-commands">JDA's
     * slash-commands Wiki article</a> for more information.
     *
     * @param event The relating {@link SlashCommandEvent}
     */
    default void onSlashCommand(SlashCommandEvent event) {}

    /**
     * The execute method for when a button related to this command gets clicked. <br>
     * Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#buttons">JDA's
     * buttons Wiki article</a> for more information.
     *
     * @param event The relating {@link ButtonClickEvent}
     * @param idArgs All the arguments stored in the ID ({@link Component#getId()
     *        Component#getId()}) have been converted to a {@link List} The commands name and
     *        current time in MS have been removed from this, example:
     *        "examplecommand-7272727272-userId", this will be converted to a {@link List}
     *        containing "userId", the rest has been removed.
     */
    default void onButtonClick(ButtonClickEvent event, List<String> idArgs) {}

    /**
     * The execute method for when a selection menu related to this command gets clicked. <br>
     * JDA has no dedicated guide for selection menu's, they work the same as buttons. <br>
     * Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#buttons">JDA's
     * buttons Wiki article</a> for more information.
     *
     * @param event The relating {@link ButtonClickEvent}
     * @param idArgs All the arguments stored in the ID ({@link Component#getId()
     *        Component#getId()}) have been converted to a {@link List} The commands name and
     *        current time in MS have been removed from this, example:
     *        "examplecommand-7272727272-userId", this will be converted to a {@link List}
     *        containing "userId", the rest has been removed.
     */
    default void onSelectionMenu(SelectionMenuEvent event, List<String> idArgs) {}


    /**
     * Generates a {@link net.dv8tion.jda.api.interactions.components.Component Component} ID based
     * on the command name, current time and given arguments, <br>
     * {@link net.dv8tion.jda.api.interactions.components.Component Component} examples are
     * {@link net.dv8tion.jda.api.interactions.components.Button Buttons} and or
     * {@link net.dv8tion.jda.api.interactions.components.selections.SelectionMenu SelectionMenu's}
     *
     * <br>
     * An ID can be maximum of
     * {@link net.dv8tion.jda.api.interactions.components.Button#ID_MAX_LENGTH} chars. <br>
     * Between every argument an `{@code -}` is added, and by default the command name + current
     * time in MS gets added.
     *
     * @param args A {@link String} array of the arguments
     * @return A {@link String} generated ID in the format of
     *         `{@code commandName-timeInMs-arg1-arg2}`
     */
    default @NotNull String generateComponentId(@NotNull String... args) {
        StringBuilder stringBuilder = new StringBuilder(getCommandName());

        stringBuilder.append("-").append(Instant.now().getNano());

        for (String arg : args) {
            stringBuilder.append("-").append(arg);
        }

        return stringBuilder.toString();
    }

    /**
     * /** Generates a {@link net.dv8tion.jda.api.interactions.components.Component Component} ID
     * based on the command name, current time and given arguments, <br>
     * {@link net.dv8tion.jda.api.interactions.components.Component Component} examples are
     * {@link net.dv8tion.jda.api.interactions.components.Button Buttons} and or
     * {@link net.dv8tion.jda.api.interactions.components.selections.SelectionMenu SelectionMenu's}
     *
     * <br>
     * An ID can be maximum of
     * {@link net.dv8tion.jda.api.interactions.components.Button#ID_MAX_LENGTH} chars. <br>
     * Between every argument an `{@code -}` is added, and by default the command name + current
     * time in MS gets added.
     *
     * @param args A {@link Collection<String>} of {@link String}'s of the arguments
     * @return A {@link String} generated ID in the format of
     *         `{@code commandName-timeInMs-arg1-arg2}`
     */
    default @NotNull String generateComponentId(@NotNull Collection<String> args) {
        return generateComponentId(args.toArray(new String[] {}));
    }
}
