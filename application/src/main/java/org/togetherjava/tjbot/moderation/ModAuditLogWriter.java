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
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility class that allows you to easily log an entry on the mod audit log channel. Thread-Safe.
 */
public enum ModAuditLogWriter {
    ;
    private static final Color EMBED_COLOR = Color.decode("#3788AC");

    private static final Logger logger = LoggerFactory.getLogger(ModAuditLogWriter.class);

    private static final Predicate<String> isAuditLogChannelName =
            Pattern.compile(Config.getInstance().getModAuditLogChannelPattern()).asMatchPredicate();
    private static final Predicate<TextChannel> isAuditLogChannel =
            channel -> isAuditLogChannelName.test(channel.getName());

    /**
     * Sends a log embed on the mod audit log channel.
     *
     * @param title the title of the log embed
     *
     * @param description the description of the log embed
     *
     * @param author the user to be added to the embed
     *
     * @param timestamp the timestamp to be added to the embed
     *
     * @param guild the guild to write this log to
     *
     * @param attachments the attachments that'll be added to the message
     */
    public static void writeModAuditLog(@NotNull String title, @NotNull String description,
            @NotNull User author, @NotNull TemporalAccessor timestamp, @NotNull Guild guild,
            @NotNull List<@NotNull Attachment> attachments) {
        Optional<TextChannel> auditLogChannel = getModAuditLogChannel(guild);
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
            message = message.addFile(attachment.getContent(), attachment.getName());
        }
        message.queue();
    }

    /**
     * Sends a log embed on the mod audit log channel.
     *
     * @param title the title of the log embed
     *
     * @param description the description of the log embed
     *
     * @param author the user to be added to the embed
     *
     * @param timestamp the timestamp to be added to the embed
     *
     * @param guild the guild to write this log to
     *
     * @param attachment an attachment that'll be added to the message
     */
    public static void writeModAuditLog(@NotNull String title, @NotNull String description,
            @NotNull User author, @NotNull TemporalAccessor timestamp, @NotNull Guild guild,
            @NotNull Attachment attachment) {
        writeModAuditLog(title, description, author, timestamp, guild, List.of(attachment));
    }

    /**
     * Sends a log embed on the mod audit log channel.
     *
     * @param title the title of the log embed
     *
     * @param description the description of the log embed
     *
     * @param author the user to be added to the embed
     *
     * @param timestamp the timestamp to be added to the embed
     *
     * @param guild the guild to write this log to
     */
    public static void writeModAuditLog(@NotNull String title, @NotNull String description,
            @NotNull User author, @NotNull TemporalAccessor timestamp, @NotNull Guild guild) {
        writeModAuditLog(title, description, author, timestamp, guild, List.of());
    }

    /**
     * Gets the channel used for moderation audit logs, if present. If the channel doesn't exist,
     * this method will return an empty optional, and a warning message will be written.
     *
     * @param guild the guild to look for the channel in
     */
    public static Optional<TextChannel> getModAuditLogChannel(@NotNull Guild guild) {
        Optional<TextChannel> channel =
                guild.getTextChannelCache().stream().filter(isAuditLogChannel).findAny();
        if (channel.isEmpty()) {
            logger.warn(
                    "Unable to log moderation events, did not find a mod audit log channel matching the configured pattern '{}' for guild '{}'",
                    Config.getInstance().getModAuditLogChannelPattern(), guild.getName());
        }
        return channel;
    }

    /**
     * Represents attachment to messages, as for example used by
     * {@link MessageAction#addFile(File, String, AttachmentOption...)}.
     * 
     * @param name the name of the attachment, example: {@code "foo.md"}
     * @param content the content of the attachment
     */
    public static final record Attachment(@NotNull String name, @NotNull String content) {
        /**
         * @return the name of the file. used by JDA methods
         */
        public @NotNull String getName() {
            return name;
        }

        /**
         * @return the content of the file as a {@code byte[]}. used by JDA methods
         */
        public byte @NotNull [] getContent() {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }
}
