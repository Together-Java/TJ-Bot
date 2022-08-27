package org.togetherjava.tjbot.commands.moderation.attachment;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.jetbrains.annotations.NotNull;
import org.jooq.tools.StringUtils;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Listener that receives all sent messages in every channel and checks if the message has any
 * blacklisted file attached.
 * <p>
 * If there was a blacklisted file attached, delete the message and send the user a dm, telling what
 * they did wrong and also inform the mods.
 */
public class AttachmentListener extends MessageReceiverAdapter {

    private final Config config;

    private final ModAuditLogWriter modAuditLogWriter;

    /**
     * Creates a AttachmentListener to receive all message sent in any channel.
     *
     * @param config to find the blacklisted media attachments
     * @param modAuditLogWriter to inform the mods about the suspicious attachment
     */
    public AttachmentListener(@NotNull Config config,
            @NotNull ModAuditLogWriter modAuditLogWriter) {
        super(Pattern.compile(".*"));
        this.config = config;
        this.modAuditLogWriter = modAuditLogWriter;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        if (attachmentContainsBlacklistedFileExtension(event)) {
            deleteMessage(event).flatMap(any -> dmUser(event)).queue();
            warnMods(event);
        }
    }

    private AuditableRestAction<Void> deleteMessage(@NotNull MessageReceivedEvent event) {
        return event.getMessage().delete();
    }

    private RestAction<Message> dmUser(@NotNull MessageReceivedEvent event) {
        return dmUser(event.getMessage(), event.getAuthor().getIdLong(), event.getJDA());
    }

    private RestAction<Message> dmUser(Message originalMessage, long userId, @NotNull JDA jda) {
        Message dmMessage = createDmMessage(originalMessage);
        return jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(dmMessage));
    }

    private Message createDmMessage(Message originalMessage) {
        String originalMessageContent = originalMessage.getContentDisplay();
        String blacklistedAttachments = getBlacklistedAttachmentsFromMessage(originalMessage);
        String dmMessageContent =
                "Hey there, you posted a message with a blacklisted file attachment: %s. Following file extension are blacklisted: %s ."
                    .formatted(blacklistedAttachments, config.getBlacklistedFileExtensions());
        // No embedded needed if there was no message from the user
        if (StringUtils.isEmpty(originalMessageContent)) {
            return new MessageBuilder(dmMessageContent).build();
        }
        return createMessageWithOriginalMessageAsEmbedded(originalMessageContent, dmMessageContent);
    }

    private Message createMessageWithOriginalMessageAsEmbedded(String originalMessageContent,
            String dmMessageContent) {
        MessageEmbed originalMessageEmbed =
                new EmbedBuilder().setDescription(originalMessageContent)
                    .setColor(Color.ORANGE)
                    .build();
        return new MessageBuilder(dmMessageContent).setEmbeds(originalMessageEmbed).build();
    }

    private String getBlacklistedAttachmentsFromMessage(Message originalMessage) {
        List<String> blacklistedAttachments = new ArrayList<>();
        originalMessage.getAttachments()
            .stream()
            .filter(attachment -> config.getBlacklistedFileExtensions()
                .contains(attachment.getFileExtension()))
            .forEach(attachment -> blacklistedAttachments.add(attachment.getFileName()));
        return String.join(", ", blacklistedAttachments);
    }

    private boolean attachmentContainsBlacklistedFileExtension(MessageReceivedEvent event) {
        Message message = event.getMessage();
        List<Message.Attachment> attachments = message.getAttachments();
        if (attachments.isEmpty()) {
            return false;
        }
        return attachments.stream()
            .anyMatch(attachment -> config.getBlacklistedFileExtensions()
                .contains(attachment.getFileExtension()));
    }

    private void warnMods(@NotNull MessageReceivedEvent event) {
        Message sentUserMessage = event.getMessage();
        String blacklistedAttachmentsFromMessage =
                getBlacklistedAttachmentsFromMessage(sentUserMessage);
        modAuditLogWriter.write(
                "Message with blacklisted attachment detected: %s"
                    .formatted(blacklistedAttachmentsFromMessage),
                "Sent Message: %s".formatted(sentUserMessage), event.getAuthor(),
                sentUserMessage.getTimeCreated(), event.getGuild());
    }
}
