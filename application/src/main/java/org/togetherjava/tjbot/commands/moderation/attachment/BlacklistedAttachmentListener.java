package org.togetherjava.tjbot.commands.moderation.attachment;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.jooq.tools.StringUtils;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reacts to blacklisted attachmenents being posted, upon which they are deleted.
 */
public class BlacklistedAttachmentListener extends MessageReceiverAdapter {
    private final ModAuditLogWriter modAuditLogWriter;
    private final List<String> blacklistedFileExtensions;

    /**
     * Creates a AttachmentListener to receive all message sent in any channel.
     *
     * @param config to find the blacklisted media attachments
     * @param modAuditLogWriter to inform the mods about the suspicious attachment
     */
    public BlacklistedAttachmentListener(@NotNull Config config,
            @NotNull ModAuditLogWriter modAuditLogWriter) {
        super(Pattern.compile(".*"));
        this.modAuditLogWriter = modAuditLogWriter;
        this.blacklistedFileExtensions = config.getBlacklistedFileExtensions();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        if (doesMessageContainBlacklistedContent(event.getMessage())) {
            handleBadMessage(event);
            warnMods(event);
        }
    }

    private void handleBadMessage(@NotNull MessageReceivedEvent event) {
        event.getMessage().delete().flatMap(any -> dmUser(event)).queue();
    }

    private RestAction<Message> dmUser(@NotNull MessageReceivedEvent event) {
        Message dmMessage = createDmMessage(event.getMessage());
        return event.getJDA()
            .openPrivateChannelById(event.getAuthor().getIdLong())
            .flatMap(channel -> channel.sendMessage(dmMessage));
    }

    private Message createDmMessage(Message originalMessage) {
        String originalMessageContent = originalMessage.getContentDisplay();
        String blacklistedAttachments = getBlacklistedAttachmentsFromMessage(originalMessage);
        String dmMessageContent =
                "Hey there, you posted a message with a blacklisted file attachment: %s. Following file extension are blacklisted: %s ."
                    .formatted(blacklistedAttachments, blacklistedFileExtensions);
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
        List<String> blacklistedAttachments = originalMessage.getAttachments()
            .stream()
            .filter(attachment -> blacklistedFileExtensions.contains(attachment.getFileExtension()))
            .map(Message.Attachment::getFileName)
            .toList();
        return String.join(", ", blacklistedAttachments);
    }

    private boolean doesMessageContainBlacklistedContent(Message message) {
        List<Message.Attachment> attachments = message.getAttachments();
        return attachments.stream()
            .anyMatch(attachment -> blacklistedFileExtensions
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
