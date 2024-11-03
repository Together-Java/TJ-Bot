package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;

public final class PinAnswerCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final String COMMAND_NAME = "pin-answer";
    private static final int MAX_PINNED_ANSWERS = 3;
    private int count = 0;

    public PinAnswerCommand() {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        Message originalMessage = event.getTarget();
        User commandInvoker = event.getUser();

        if (!(originalMessage.getChannel() instanceof ThreadChannel threadChannel)) {
            replyNotInThread(event);
            return;
        }

        if (!isThreadCreator(commandInvoker, threadChannel)) {
            replyNotThreadCreator(event);
            return;
        }

        if (count >= MAX_PINNED_ANSWERS) {
            replyMaxPinsReached(event);
            return;
        }

        pinMessage(event, originalMessage);
    }

    private boolean isThreadCreator(User user, ThreadChannel thread) {
        return user.getIdLong() == thread.getOwnerIdLong();
    }

    private void pinMessage(MessageContextInteractionEvent event, Message message) {
        message.pin().queue(success -> {
            count++;
            event.reply("Answer pinned successfully! Pinned answers: " + count)
                .setEphemeral(true)
                .queue();
        }, failure -> event.reply("Failed to pin the answer.").setEphemeral(true).queue());
    }

    private void replyNotInThread(MessageContextInteractionEvent event) {
        event.reply("This message is not in a thread.").setEphemeral(true).queue();
    }

    private void replyNotThreadCreator(MessageContextInteractionEvent event) {
        event.reply("You are not the thread creator and cannot pin answers here.")
            .setEphemeral(true)
            .queue();
    }

    private void replyMaxPinsReached(MessageContextInteractionEvent event) {
        event.reply("Maximum pinned answers (" + MAX_PINNED_ANSWERS + ") reached.")
            .setEphemeral(true)
            .queue();
    }
}
