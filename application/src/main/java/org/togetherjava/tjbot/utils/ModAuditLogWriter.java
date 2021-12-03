package org.togetherjava.tjbot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ModAuditLogWriter {
    private static final Logger logger = LoggerFactory.getLogger(ModAuditLogWriter.class);

    private static final Predicate<String> isAuditLogChannelName =
            Pattern.compile(Config.getInstance().getModAuditLogChannelPattern()).asMatchPredicate();
    private static final Predicate<TextChannel> isAuditLogChannel =
            channel -> isAuditLogChannelName.test(channel.getName());

    private ModAuditLogWriter() {
        throw new IllegalStateException("Utility class");
    }

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
            VirtualFile... files) {
        Optional<TextChannel> auditLogChannel = getModAuditLogChannel(guild);
        if (auditLogChannel.isEmpty()) {
            logger.warn(
                    "Unable to log moderation events, did not find a mod audit log channel matching the configured pattern '{}' for guild '{}'",
                    Config.getInstance().getModAuditLogChannelPattern(), guild.getName());
            return;
        }

        auditLogChannel.get().sendMessageEmbeds(embed.build()).queue();
        for (VirtualFile file : files) {
            auditLogChannel.get().sendFile(file.getAsInputStream(), file.getName()).queue();
        }
    }


    /**
     * stolen from {@link org.togetherjava.tjbot.routines.ModAuditLogRoutine}
     */
    private static Optional<TextChannel> getModAuditLogChannel(@NotNull Guild guild) {
        return guild.getTextChannelCache()
            .stream()
            .filter(isAuditLogChannel)
            .findAny();
    }
}
