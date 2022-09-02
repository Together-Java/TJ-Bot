package org.togetherjava.tjbot.features.moderation.attachment;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.moderation.ModAuditLogWriter;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reacts to blacklisted attachments being posted, upon which they are deleted.
 */
public final class BlacklistedAttachmentListener extends MessageReceiverAdapter {
    private final ModAuditLogWriter modAuditLogWriter;
    private final List<String> blacklistedFileExtensions;

    /**
     * Creates a AttachmentListener to receive all message sent in any channel.
     *
     * @param config to find the blacklisted media attachments
     * @param modAuditLogWriter to inform the mods about the suspicious attachment
     */
    public BlacklistedAttachmentListener(Config config, ModAuditLogWriter modAuditLogWriter) {
        super(Pattern.compile(".*"));
        this.modAuditLogWriter = modAuditLogWriter;
        blacklistedFileExtensions = config.getBlacklistedFileExtensions();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        if (doesMessageContainBlacklistedContent(event.getMessage())) {
            handleBadMessage(event.getMessage());
        }
    }

    private void handleBadMessage(Message message) {
        message.delete().flatMap(any -> dmUser(message)).queue(any -> warnMods(message));
    }

    private RestAction<Message> dmUser(Message message) {
        Message dmMessage = createDmMessage(message);
        return message.getAuthor()
            .openPrivateChannel()
            .flatMap(privateChannel -> privateChannel.sendMessage(dmMessage));
    }

    private Message createDmMessage(Message originalMessage) {
        String contentRaw = originalMessage.getContentRaw();
        String blacklistedAttachments =
                String.join(", ", getBlacklistedAttachmentsFromMessage(originalMessage));

        String dmMessageContent =
                """
                        Hey there, you posted a message containing a blacklisted file attachment: %s.
                        We had to delete your message for security reasons.

                        Feel free to repost your message without, or with a different file instead. Sorry for any inconvenience caused by this 🙇️
                        """
                    .formatted(blacklistedAttachments);

        // No embed needed if there was no message from the user
        if (contentRaw.isEmpty()) {
            return new MessageBuilder(dmMessageContent).build();
        }
        return createBaseResponse(contentRaw, dmMessageContent);
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
                .contains(attachment.getFileExtension().toLowerCase(Locale.US)))
            .map(Message.Attachment::getFileName)
            .toList();
    }

    private boolean doesMessageContainBlacklistedContent(Message message) {
        return message.getAttachments()
            .stream()
            .anyMatch(attachment -> blacklistedFileExtensions
                .contains(attachment.getFileExtension().toLowerCase(Locale.US)));
    }

    private void warnMods(Message sentUserMessage) {
        String blacklistedAttachmentsFromMessage =
                String.join(", ", getBlacklistedAttachmentsFromMessage(sentUserMessage));

        modAuditLogWriter.write(
                "Message with blacklisted content prevented: %s"
                    .formatted(blacklistedAttachmentsFromMessage),
                "Sent Message: %s".formatted(sentUserMessage), sentUserMessage.getAuthor(),
                sentUserMessage.getTimeCreated(), sentUserMessage.getGuild());
    }
}
