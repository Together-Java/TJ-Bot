package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.togetherjava.tjbot.commands.example.CommandExample;

/**
 * The class all commands have to abstract.
 * <br> Look at {@link CommandExample} for an example.
 * <br> A second example will come for sub-commands.
 *
 */
public abstract class AbstractCommand {

    /**
     * The commands name.
     *
     * @return
     *  The commands name as a {@link String}
     */
    public abstract String getCommandName();

    /**
     * The command's description.
     *
     * @return
     *  The commands description as a {@link String}
     */
    public abstract String getDescription();

    /**
     * Whenever the command is only for guilds, optional method.
     *
     * @return
     *  Whenever the command is only for guilds as a {@link Boolean}
     */
    public boolean isGuildOnly() {
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
    public CommandData addOptions(CommandData commandData) {
        return commandData;
    }

    /**
     * The execute method for when the command gets executed.
     * <br> Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#slash-commands">JDA's slash-commands Wiki article</a> for more information.
     *
     * @param event
     *  The relating {@link SlashCommandEvent}
     */
    public abstract void execute(SlashCommandEvent event);
}
