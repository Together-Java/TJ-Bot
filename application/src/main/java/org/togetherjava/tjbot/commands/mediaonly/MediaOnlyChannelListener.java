package org.togetherjava.tjbot.commands.mediaonly;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages from the Media Only Channels, checks if the message has
 * media attached.
 * <p>
 * If there was no media attached, delete the message and send the user a DM, telling what they did
 * wrong.
 */
public final class MediaOnlyChannelListener extends MessageReceiverAdapter {

    /**
     * Creates a MediaOnlyChannelListener to receive all message sent in MediaOnly channel.
     *
     * @param config to find MediaOnly channels
     */
    public MediaOnlyChannelListener(Config config) {
        super(Pattern.compile(config.getMediaOnlyChannelPattern()));
    }


    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        if (messageHasNoMediaAttached(event)) {
            deleteMessage(event).flatMap(any -> dmUser(event)).queue();
        }
    }

    private static boolean messageHasNoMediaAttached(MessageReceivedEvent event) {
        Message message = event.getMessage();
        return message.getAttachments().isEmpty() && message.getEmbeds().isEmpty()
                && !message.getContentRaw().contains("http");
    }

    @Nonnull
    private AuditableRestAction<Void> deleteMessage(MessageReceivedEvent event) {
        return event.getMessage().delete();
    }

    @Nonnull
    private RestAction<Message> dmUser(MessageReceivedEvent event) {
        return dmUser(event.getMessage());
    }

    @Nonnull
    private RestAction<Message> dmUser(Message originalMessage) {
        String originalMessageContent = originalMessage.getContentRaw();
        MessageEmbed originalMessageEmbed =
                new EmbedBuilder().setDescription(originalMessageContent)
                    .setColor(Color.ORANGE)
                    .build();

        Message dmMessage = new MessageBuilder(
                "Hey there, you posted a message without media (image, video, link) in a media-only channel. Please see the description of the channel for details and then repost with media attached, thanks 😀")
                    .setEmbeds(originalMessageEmbed)
                    .build();

        return originalMessage.getAuthor()
            .openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(dmMessage));
    }
}
