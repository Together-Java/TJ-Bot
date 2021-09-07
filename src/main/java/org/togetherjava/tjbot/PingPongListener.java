package org.togetherjava.tjbot;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PingPongListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PingPongListener.class);

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor()
                 .isBot()) {
            return;
        }
        if (!event.isFromType(ChannelType.TEXT)) {
            return;
        }

        if (!"!ping".equals(event.getMessage()
                                 .getContentDisplay())) {
            return;
        }

        logger.info("#{}: Received '!ping' command", event.getResponseNumber());

        event.getChannel()
             .sendMessage("Pong!")
             .queue();
    }
}
