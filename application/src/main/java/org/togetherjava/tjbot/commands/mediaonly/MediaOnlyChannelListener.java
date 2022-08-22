package org.togetherjava.tjbot.commands.mediaonly;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;

import java.awt.*;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages from the Media Only Channels, checks if the message has
 * media attched.
 * <p>
 * If there was no media attached, delete the messagen and send the User a DM, telling what they did
 * wrong.
 */
public final class MediaOnlyChannelListener extends MessageReceiverAdapter {

    /**
     * Creates a MediaOnlyChannelListener to receive all message sent in MediaOnly channel.
     *
     * @param config to find MediaOnly channels
     */
    public MediaOnlyChannelListener(@NotNull Config config) {
        super(Pattern.compile(config.getMediaOnlyChannelPattern()));
    }


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        if (messageHasNoMediaAttached(event)) {
            deleteMessage(event).flatMap(any -> dmUser(event)).queue();
        }
    }

    private static boolean messageHasNoMediaAttached(MessageReceivedEvent event) {
        Message message = event.getMessage();
        return message.getAttachments().isEmpty() && message.getEmbeds().isEmpty();
    }

    private AuditableRestAction<Void> deleteMessage(@NotNull MessageReceivedEvent event) {
        return event.getMessage().delete();
    }

    private RestAction<Message> dmUser(@NotNull MessageReceivedEvent event) {
        return dmUser(event.getMessage(), event.getAuthor().getIdLong(), event.getJDA());
    }

    private RestAction<Message> dmUser(Message sentMessage, long userId, @NotNull JDA jda) {
        String sentMessageContent = sentMessage.getContentDisplay();
        MessageEmbed sendMessageEmbed = new EmbedBuilder().setDescription(sentMessageContent)
            .setColor(Color.ORANGE)
            .build();
        Message dmMessage = new MessageBuilder(
                "Hey there, your were posting a Message without a Media attached, please attach some media (URL or other Media) to your message 😀.")
                    .setEmbeds(sendMessageEmbed)
                    .build();
        return jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(dmMessage));
    }
}
