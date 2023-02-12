package org.togetherjava.tjbot.features.mediaonly;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.awt.Color;
import java.util.concurrent.TimeUnit;
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

        Message message = event.getMessage();
        if (message.getType() == MessageType.THREAD_CREATED) {
            return;
        }

        if (messageHasNoMediaAttached(message)) {
            message.delete().flatMap(any -> dmUser(message)).queue(res -> {
            }, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, err -> {
                warnUser(message).queue((res) -> {
                    res.delete().queueAfter(1, TimeUnit.MINUTES);
                });
            }));
        }
    }

    private boolean messageHasNoMediaAttached(Message message) {
        return message.getAttachments().isEmpty() && message.getEmbeds().isEmpty()
                && !message.getContentRaw().contains("http");
    }

    private MessageEmbed originalMessageEmbed(Message message) {
        String originalMessageContent = message.getContentRaw();
        return new EmbedBuilder().setDescription(originalMessageContent)
                .setColor(Color.ORANGE)
                .build();
    }

    private RestAction<Message> warnUser(Message message) {
        MessageEmbed originalMessageEmbed = originalMessageEmbed(message);

        long authorId = message.getAuthor().getIdLong();

        MessageCreateData pingMessage = new MessageCreateBuilder().setContent("Hey there, you <@"
                        + authorId
                        + "> posted a message without media (image, video, link) in a media-only channel. Please see the description of the channel for details and then repost with media attached, thanks 😀")
                .setEmbeds(originalMessageEmbed)
                .build();

        return message.getChannel().sendMessage(pingMessage);
    }

    private RestAction<Message> dmUser(Message message) {
        MessageEmbed originalMessageEmbed = originalMessageEmbed(message);

        MessageCreateData dmMessage = new MessageCreateBuilder().setContent(
                        "Hey there, you posted a message without media (image, video, link) in a media-only channel. Please see the description of the channel for details and then repost with media attached, thanks 😀")
                .setEmbeds(originalMessageEmbed)
                .build();

        return message.getAuthor()
                .openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(dmMessage));
    }
}
