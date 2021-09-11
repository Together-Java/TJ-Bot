package org.togetherjava.tjbot;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of an example command to illustrate how to use JDA.
 * <p>
 * The implemented command is {@code !ping}. The bot will respond with a message {@code Pong!}.
 * <p>
 * For example:
 *
 * <pre>
 * {@code
 * !ping
 * // TJ-Bot: Pong!
 * }
 * </pre>
 */
public final class PingPongListener extends ListenerAdapter {
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(PingPongListener.class);

    /**
     * Handler for the {@code !ping} command. Will ignore any message that is not exactly
     * {@code !ping}.
     *
     * @param event the event the message belongs to
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (!event.isFromType(ChannelType.TEXT)) {
            return;
        }

        if (!"!ping".equals(event.getMessage().getContentDisplay())) {
            return;
        }

        logger.info("#{}: Received '!ping' command", event.getResponseNumber());

        event.getChannel().sendMessage("Pong!").queue();
    }
}
