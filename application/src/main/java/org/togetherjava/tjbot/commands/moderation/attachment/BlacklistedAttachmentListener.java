package org.togetherjava.tjbot.commands.moderation.attachment;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

import java.awt.*;
import java.util.List;
import java.util.Objects;
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
            handleBadMessage(event.getMessage());
            warnMods(event.getMessage());
        }
    }

    private void handleBadMessage(@NotNull Message message) {
        message.delete().flatMap(any -> dmUser(message)).queue();
    }

    private RestAction<Message> dmUser(@NotNull Message message) {
        Message dmMessage = createDmMessage(message);
        return message.getJDA()
            .openPrivateChannelById(message.getAuthor().getIdLong())
            .flatMap(channel -> channel.sendMessage(dmMessage));
    }

    private Message createDmMessage(Message originalMessage) {
        String originalMessageContent = originalMessage.getContentDisplay();
        String blacklistedAttachments =
                String.join(", ", getBlacklistedAttachmentsFromMessage(originalMessage));
        String dmMessageContent =
                "Hey there, you posted a message with a blacklisted file attachment: %s. Following file extension are blacklisted: %s ."
                    .formatted(blacklistedAttachments, blacklistedFileExtensions);
        // No embedded needed if there was no message from the user
        if (originalMessageContent.isEmpty()) {
            return new MessageBuilder(dmMessageContent).build();
        }
        return createBaseResponse(originalMessageContent, dmMessageContent);
    }

    private Message createBaseResponse(String originalMessageContent, String dmMessageContent) {
        MessageEmbed originalMessageEmbed =
                new EmbedBuilder().setDescription(originalMessageContent)
                    .setColor(Color.ORANGE)
                    .build();
        return new MessageBuilder(dmMessageContent).setEmbeds(originalMessageEmbed).build();
    }

    private List<String> getBlacklistedAttachmentsFromMessage(Message originalMessage) {
        return originalMessage.getAttachments()
            .stream()
            .filter(attachment -> blacklistedFileExtensions
                .contains(Objects.requireNonNull(attachment.getFileExtension()).toLowerCase()))
            .map(Message.Attachment::getFileName)
            .toList();
    }

    private boolean doesMessageContainBlacklistedContent(Message message) {
        List<Message.Attachment> attachments = message.getAttachments();
        return attachments.stream()
            .anyMatch(attachment -> blacklistedFileExtensions
                .contains(Objects.requireNonNull(attachment.getFileExtension()).toLowerCase()));
    }


    private void warnMods(@NotNull Message sentUserMessage) {
        String blacklistedAttachmentsFromMessage =
                String.join(", ", getBlacklistedAttachmentsFromMessage(sentUserMessage));
        modAuditLogWriter.write(
                "Message with blacklisted content detected: %s"
                    .formatted(blacklistedAttachmentsFromMessage),
                "Sent Message: %s".formatted(sentUserMessage), sentUserMessage.getAuthor(),
                sentUserMessage.getTimeCreated(), sentUserMessage.getGuild());
    }
}
