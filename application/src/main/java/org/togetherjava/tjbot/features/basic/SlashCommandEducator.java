package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SlashCommandEducator extends MessageReceiverAdapter {
    private static final Predicate<String> IS_MESSAGE_COMMAND = Pattern.compile("""
            [.!?] #Start of message command
            [a-zA-Z]{2,} #Name of message command, e.g. 'close'
            .* #Rest of the message
            """, Pattern.COMMENTS).asMatchPredicate();

    public SlashCommandEducator() {
        super(Pattern.compile(".*"));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String content = event.getMessage().getContentRaw();
        if (IS_MESSAGE_COMMAND.test(content)) {
            sendAdvice(event.getMessage());
        }
    }

    private void sendAdvice(Message message) {
        // TODO Implement proper advice
        message.reply("huhu").queue();
    }
}
