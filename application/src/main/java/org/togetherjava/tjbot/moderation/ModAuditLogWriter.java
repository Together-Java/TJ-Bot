package org.togetherjava.tjbot.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * used to log activities in the mod audit log channel
 */
public enum ModAuditLogWriter {;
    private static final Logger logger = LoggerFactory.getLogger(ModAuditLogWriter.class);

    private static final Predicate<String> isAuditLogChannelName =
            Pattern.compile(Config.getInstance().getModAuditLogChannelPattern()).asMatchPredicate();
    private static final Predicate<TextChannel> isAuditLogChannel =
            channel -> isAuditLogChannelName.test(channel.getName());

    /**
     * logs an entry in the mod audit log channel.
     * 
     * @param guild the guild. usually use {@link GenericInteractionCreateEvent#getGuild()}
     *
     * @param embed the embed that will be sent.
     *
     * @param files the files added to the message.
     */
    public static void log(@NotNull Guild guild, @NotNull EmbedBuilder embed,
            Attachment... files) {
        Optional<TextChannel> auditLogChannel = getModAuditLogChannel(guild);
        if (auditLogChannel.isEmpty()) {
            logger.warn(
                    "Unable to log moderation events, did not find a mod audit log channel matching the configured pattern '{}' for guild '{}'",
                    Config.getInstance().getModAuditLogChannelPattern(), guild.getName());
            return;
        }

        MessageAction message = auditLogChannel.get().sendMessageEmbeds(embed.build());
        for (Attachment file : files) {
            message = message.addFile(file.getContent(), file.getName());
        }
        message.queue();
    }

    /**
     * stolen from {@link org.togetherjava.tjbot.routines.ModAuditLogRoutine}
     */
    private static Optional<TextChannel> getModAuditLogChannel(@NotNull Guild guild) {
        return guild.getTextChannelCache().stream().filter(isAuditLogChannel).findAny();
    }

    /**
     * used to add a file to a message without having an actual file.
     * 
     * @param name the name of the file, example: {@code "foo.md"}
     * @param content the content of the file
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
        public byte[] getContent() {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }
}
