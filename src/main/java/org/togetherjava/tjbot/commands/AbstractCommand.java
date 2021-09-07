package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;

/**
 * The class all commands have to abstract.
 * Look at {@link org.togetherjava.tjbot.commands.example.ExampleCommand} for an example.
 * A second example will come for sub-commands.
 */
public abstract class AbstractCommand {

    /**
     * The commands name.
     * @return
     *  The commands name as a {@link String}
     */
    public abstract String getCommandName();

    /**
     * The commands description.
     * @return
     *  The commands description as a {@link String}
     */
    public abstract String getDescription();

    /**
     * Whenever the command is only for guilds, optional method.
     * @return
     *  Whenever the command is only for guilds as a {@link Boolean}
     */
    public boolean isGuildOnly() {
        return false;
    }

    /**
     * This command will add all the options to the slash-command.
     * Look at {@link org.togetherjava.tjbot.commands.example.ExampleCommand#addOptions(CommandCreateAction)} for an example.
     * @param commandCreateAction
     *  The {@link CommandCreateAction} where the options need to be added.
     * @return
     *  The changed {@link CommandCreateAction}
     */
    public abstract CommandCreateAction addOptions(CommandCreateAction commandCreateAction);

    /**
     * The execute method for when the command gets executed.
     * Check <a href="https://github.com/DV8FromTheWorld/JDA/wiki/Interactions#slash-commands">JDA's slash-commands Wiki article</a> for more information.
     * @param event
     *  The relating {@link SlashCommandEvent}
     */
    public abstract void execute(SlashCommandEvent event);
}
