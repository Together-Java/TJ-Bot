package org.togetherjava.tjbot.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility class that allows you to easily log an entry on the mod audit log channel. Thread-Safe.
 * <br />
 * <br />
 * Use {@link ModAuditLogWriter#write(String, String, User, TemporalAccessor, Guild, Attachment...)}
 * to log an entry.
 */
public final class ModAuditLogWriter {
    private static final Color EMBED_COLOR = Color.decode("#3788AC");

    private static final Logger logger = LoggerFactory.getLogger(ModAuditLogWriter.class);

    private final Config config;

    private final Pattern auditLogChannelNamePattern;

    /**
     * Creates a new instance.
     *
     * @param config the config to use for this
     */
    public ModAuditLogWriter(@NotNull Config config) {
        this.config = config;
        auditLogChannelNamePattern = Pattern.compile(config.getModAuditLogChannelPattern());
    }

    /**
     * Sends a log on the mod audit log channel.
     *
     * @param title the title of the log embed
     * @param description the description of the log embed
     * @param author the author of the log message
     * @param timestamp the timestamp of the log message
     * @param guild the guild to write this log to
     * @param attachments attachments that will be added to the message. none or many.
     */
    public void write(@NotNull String title, @NotNull String description, @NotNull User author,
            @NotNull TemporalAccessor timestamp, @NotNull Guild guild,
            @NotNull Attachment... attachments) {
        Optional<TextChannel> auditLogChannel = getAndHandleModAuditLogChannel(guild);
        if (auditLogChannel.isEmpty()) {
            return;
        }

        MessageAction message = auditLogChannel.orElseThrow()
            .sendMessageEmbeds(new EmbedBuilder().setTitle(title)
                .setDescription(description)
                .setAuthor(author.getAsTag(), null, author.getAvatarUrl())
                .setTimestamp(timestamp)
                .setColor(EMBED_COLOR)
                .build());

        for (Attachment attachment : attachments) {
            message = message.addFile(attachment.getContentRaw(), attachment.name());
        }
        message.queue();
    }

    /**
     * Gets the channel used for moderation audit logs, if present. If the channel doesn't exist,
     * this method will return an empty optional, and a warning message will be written.
     *
     * @param guild the guild to look for the channel in
     */
    public Optional<TextChannel> getAndHandleModAuditLogChannel(@NotNull Guild guild) {
        Optional<TextChannel> auditLogChannel = guild.getTextChannelCache()
            .stream()
            .filter(channel -> auditLogChannelNamePattern.asMatchPredicate()
                .test(channel.getName()))
            .findAny();

        if (auditLogChannel.isEmpty()) {
            logger.warn(
                    "Unable to log moderation events, did not find a mod audit log channel matching the configured pattern '{}' for guild '{}'",
                    config.getModAuditLogChannelPattern(), guild.getName());
        }
        return auditLogChannel;
    }

    /**
     * Represents attachment to messages, as for example used by
     * {@link MessageAction#addFile(File, String, AttachmentOption...)}.
     * 
     * @param name the name of the attachment, example: {@code "foo.md"}
     * @param content the content of the attachment
     */
    public record Attachment(@NotNull String name, @NotNull String content) {
        /**
         * Gets the content raw, interpreted as UTF-8.
         * 
         * @return the raw content of the attachment
         */
        public byte @NotNull [] getContentRaw() {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }
}
