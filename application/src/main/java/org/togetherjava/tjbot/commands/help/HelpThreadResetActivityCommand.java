package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

import java.util.Comparator;
import java.util.List;

/**
 * This command will reset the activity in a help thread.
 *
 * @author mDyingStar
 */
public final class HelpThreadResetActivityCommand extends SlashCommandAdapter {

    private static final String COMMAND_NAME = "reset-activity";

    /**
     * Constructs a new HelpThreadResetActivityCommand with the command name "reset-activity", a
     * description of "resets the activity in a help thread", and command visibility of
     * {@link CommandVisibility#GUILD}.
     */
    public HelpThreadResetActivityCommand() {
        super(COMMAND_NAME, "resets the activity in a help thread", CommandVisibility.GUILD);
    }

    /**
     * Called when the command is executed. Retrieves the most recent message in the thread, adds
     * its id to the {@link HelpThreadManuallyResetHistoryCache}, and replies to the user to confirm
     * that the activity has been reset.
     *
     * @param event the event representing the command interaction
     */
    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        var helpThreadHistoryCache = HelpThreadManuallyResetHistoryCache.getInstance();
        var threadChannel = event.getChannel().asThreadChannel();

        MessagePaginationAction iterableHistory = threadChannel.getIterableHistory();

        List<Message> messages = iterableHistory.stream()
            .sorted(Comparator.comparing(ISnowflake::getTimeCreated).reversed())
            .toList();

        helpThreadHistoryCache.add(threadChannel, messages.get(0).getId());

        event.reply("Activities have been reset.").queue();
    }
}
