package org.togetherjava.tjbot.commands.meme;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;

import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages from the Meme channel, checks if the message has media
 * attched.
 * <p>
 * If there was no media attached, delete the messagen and send the User a DM, telling what they did
 * wrong.
 */
public final class MemeListener extends MessageReceiverAdapter {

    /**
     * Creates a MemeListener to receive all message sent in Memes channel.
     *
     * @param config the config to use for this
     */
    public MemeListener(@NotNull Config config) {
        super(Pattern.compile(config.getMediaOnlyChannelPattern()));
    }


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        boolean messageHasNoMediaAttached = messageHasNoMediaAttached(event);
        if (messageHasNoMediaAttached) {
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
        String contentDisplay = sentMessage.getContentDisplay();
        String dmMessage =
                ("Hey there, your were posting a Meme without a Media attached: '%s' please attach some media (URL or other Media) to your message")
                    .formatted(contentDisplay);
        return jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(dmMessage));
    }
}
