package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

/**
 * This command will reset the activity in a help thread.
 */
public class HelpThreadResetHelpActivityCommand extends SlashCommandAdapter {

    private static final String COMMAND_NAME = "reset-activity";

    public HelpThreadResetHelpActivityCommand() {
        super(COMMAND_NAME, "resets the activity in a help thread", CommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        var helpThreadHistoryCache = HelpThreadHistoryCache.getInstance();
        var threadChannel = event.getChannel().asThreadChannel();


    }
}
