package org.togetherjava.tjbot.commands.moderation.meme;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
        super(Pattern.compile(config.getMemeChannelPattern()));
    }


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        boolean mediaAttachmentEmpty = isMediaAttachmentEmpty(event);
        if (!mediaAttachmentEmpty) {
            deleteMessage(event);
            dmUser(event);
        }
    }

    private static boolean isMediaAttachmentEmpty(MessageReceivedEvent event) {
        Message message = event.getMessage();
        return !message.getAttachments().isEmpty() || !message.getEmbeds().isEmpty();
    }

    private void deleteMessage(@NotNull MessageReceivedEvent event) {
        event.getMessage().delete().queue();
    }

    private void dmUser(@NotNull MessageReceivedEvent event) {
        dmUser(event.getAuthor().getIdLong(), event.getJDA());
    }

    private void dmUser(long userId, @NotNull JDA jda) {
        String dmMessage =
                "Hey there, your were posting a Meme without a Media attached, pls attach some media (URL or other Media) "
                        + "to your message.";
        jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .queue();
    }
}
