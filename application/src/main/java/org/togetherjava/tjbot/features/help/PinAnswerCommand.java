package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;
import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;

public final class PinAnswerCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final String COMMAND_NAME = "pin-answer";
    private static final int MAX_PINNED_ANSWERS = 10;
    private final String helpForumPattern;

    public PinAnswerCommand(Config config) {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);
        HelpSystemConfig helpConfig = config.getHelpSystem();
        helpForumPattern = helpConfig.getHelpForumPattern();
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        Message originalMessage = event.getTarget();
        User commandInvoker = event.getUser();

        if (!(originalMessage.getChannel() instanceof ThreadChannel threadChannel)
                || !(threadChannel.getParentChannel() instanceof ForumChannel forumChannel)
                || !forumChannel.getName().matches(helpForumPattern)) {
            replyNotInQuestionsThread(event);
            return;
        }

        if (!threadOwner(commandInvoker, threadChannel)) {
            replyNotThreadOwner(event);
            return;
        }

        threadChannel.retrievePinnedMessages().queue(pinnedMessages -> {
            if (pinnedMessages.size() >= MAX_PINNED_ANSWERS) {
                replyMaxPinsReached(event);
            } else {
                pinMessage(event, originalMessage);
            }
        });
    }

    private boolean threadOwner(User user, ThreadChannel thread) {
        return user.getIdLong() == thread.getOwnerIdLong();
    }

    private void pinMessage(MessageContextInteractionEvent event, Message message) {
        message.pin()
            .queue(success -> event.reply("Answer pinned successfully!")
                .setEphemeral(true)
                .queue());
    }

    private void replyNotInQuestionsThread(MessageContextInteractionEvent event) {
        event.reply("This command can only be used in threads in the questions channel")
            .setEphemeral(true)
            .queue();
    }

    private void replyNotThreadOwner(MessageContextInteractionEvent event) {
        event.reply("Only thread owners can use this command").setEphemeral(true).queue();
    }

    private void replyMaxPinsReached(MessageContextInteractionEvent event) {
        event.reply("You've reached a maximum pinned limit (" + MAX_PINNED_ANSWERS
                + ") for threads, if you wish to pin more messages please remove a few existing pinned messages")
            .setEphemeral(true)
            .queue();
    }
}
