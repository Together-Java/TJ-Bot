package org.togetherjava.tjbot.features.mediaonly;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.messages.MessageSnapshot;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final Pattern MEDIA_URL_PATTERN = Pattern.compile(
            ".*https?://\\S+\\.(png|jpe?g|gif|bmp|webp|mp4|mov|avi|webm|mp3|wav|ogg|youtube\\.com/|youtu\\.com|imgur\\.com/).*",
            Pattern.CASE_INSENSITIVE);

    private final ConcurrentHashMap<Long, Long> lastValidForwardedMediaMessageTime =
            new ConcurrentHashMap<>();

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

        long userId = event.getAuthor().getIdLong();

        boolean isForwardedWithMedia =
                !message.getMessageSnapshots().isEmpty() && !messageHasNoMediaAttached(message);

        if (isForwardedWithMedia) {
            lastValidForwardedMediaMessageTime.put(userId, System.currentTimeMillis());
            return;
        }

        boolean isNormalMediaUpload =
                message.getMessageSnapshots().isEmpty() && !messageHasNoMediaAttached(message);
        if (isNormalMediaUpload) {
            return;
        }

        Long lastForwardedMediaTime = lastValidForwardedMediaMessageTime.get(userId);
        long gracePeriodMillis = TimeUnit.SECONDS.toMillis(1);

        if (lastForwardedMediaTime != null
                && (System.currentTimeMillis() - lastForwardedMediaTime) <= gracePeriodMillis) {
            lastValidForwardedMediaMessageTime.remove(userId);
            return;
        }

        message.delete().queue(deleteSuccess -> dmUser(message).queue(dmSuccess -> {
        }, dmFailure -> tempNotifyUserInChannel(message)),
                deleteFailure -> tempNotifyUserInChannel(message));
    }

    private boolean messageHasNoMediaAttached(Message message) {
        if (!message.getAttachments().isEmpty() || !message.getEmbeds().isEmpty()
                || MEDIA_URL_PATTERN.matcher(message.getContentRaw()).matches()) {
            return false;
        }

        if (!message.getMessageSnapshots().isEmpty()) {
            for (MessageSnapshot snapshot : message.getMessageSnapshots()) {
                if (!snapshot.getAttachments().isEmpty() || !snapshot.getEmbeds().isEmpty()
                        || MEDIA_URL_PATTERN.matcher(snapshot.getContentRaw()).matches()) {
                    return false;
                }
            }
            return true;
        }

        return true;
    }

    private MessageCreateData createNotificationMessage(Message message) {
        String originalMessageContent = message.getContentRaw();
        if (originalMessageContent.trim().isEmpty()) {
            originalMessageContent = "(Original message had no visible text content)";
        }

        MessageEmbed originalMessageEmbed =
                new EmbedBuilder().setDescription(originalMessageContent)
                    .setColor(Color.ORANGE)
                    .build();

        return new MessageCreateBuilder().setContent(message.getAuthor().getAsMention()
                + " Hey there, you posted a message without media (image, video, link) in a media-only channel. Please see the description of the channel for details and then repost with media attached, thanks 😀")
            .setEmbeds(originalMessageEmbed)
            .build();
    }

    private RestAction<Message> dmUser(Message message) {
        return message.getAuthor()
            .openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(createNotificationMessage(message)));
    }

    private void tempNotifyUserInChannel(Message message) {
        message.getChannel()
            .sendMessage(createNotificationMessage(message))
            .queue(notificationMessage -> notificationMessage.delete()
                .queueAfter(1, TimeUnit.MINUTES));
    }
}
