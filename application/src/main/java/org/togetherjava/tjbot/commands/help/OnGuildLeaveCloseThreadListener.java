package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpThreads;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Remove all thread channels associated to a user when they leave the guild
 */
public class OnGuildLeaveCloseThreadListener extends ListenerAdapter implements EventReceiver {
    private static final Logger logger =
            LoggerFactory.getLogger(OnGuildLeaveCloseThreadListener.class);
    private final Database database;

    public OnGuildLeaveCloseThreadListener(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent leaveEvent) {
        Set<Long> channelIds = getThreadsAssociatedToLeaver(leaveEvent);
        if (channelIds.isEmpty()) {
            return;
        }
        for (long channelId : channelIds) {
            closeThread(channelId, leaveEvent);
        }
    }

    public Set<Long> getThreadsAssociatedToLeaver(GuildMemberRemoveEvent leaveEvent) {
        return database
            .readTransaction(context -> context.select(HelpThreads.HELP_THREADS.CHANNEL_ID))
            .from(HelpThreads.HELP_THREADS)
            .where(HelpThreads.HELP_THREADS.AUTHOR_ID.eq(leaveEvent.getUser().getIdLong()))
            .fetch()
            .stream()
            .map(databaseId -> databaseId.getValue(HelpThreads.HELP_THREADS.CHANNEL_ID))
            .collect(Collectors.toSet());

    }

    public void closeThread(long channelId, GuildMemberRemoveEvent leaveEvent) {
        ThreadChannel threadChannel = leaveEvent.getGuild().getThreadChannelById(channelId);
        if (threadChannel == null) {
            logger.warn("Attempted to archive thread id: '{}' but could not find thread in guild.",
                    channelId);
            return;
        }
        MessageEmbed embed = new EmbedBuilder().setTitle("OP left")
            .setDescription("Closing thread...")
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();
        threadChannel.sendMessageEmbeds(embed)
            .flatMap(any -> threadChannel.getManager().setArchived(true))
            .queue();
    }
}
