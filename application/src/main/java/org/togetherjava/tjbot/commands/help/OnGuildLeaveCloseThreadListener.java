package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.db.Database;

import java.util.HashSet;
import java.util.Set;

import static org.togetherjava.tjbot.db.generated.tables.HelpThreads.HELP_THREADS;

/**
 * Remove all thread channels associated to a user when they leave the guild.
 */
public final class OnGuildLeaveCloseThreadListener extends ListenerAdapter
        implements EventReceiver {
    private static final Logger logger =
            LoggerFactory.getLogger(OnGuildLeaveCloseThreadListener.class);
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database database to use
     */
    public OnGuildLeaveCloseThreadListener(Database database) {
        this.database = database;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent leaveEvent) {
        Set<Long> channelIds = fetchThreadsByLeaver(leaveEvent.getUser().getIdLong());
        for (long channelId : channelIds) {
            closeThread(channelId, leaveEvent.getGuild());
        }
    }

    private Set<Long> fetchThreadsByLeaver(long leaverId) {
        return new HashSet<>(
                database.readTransaction(context -> context.select(HELP_THREADS.CHANNEL_ID))
                    .from(HELP_THREADS)
                    .where(HELP_THREADS.AUTHOR_ID.eq(leaverId))
                    .fetch(databaseMapper -> databaseMapper.getValue(HELP_THREADS.CHANNEL_ID)));
    }

    private void closeThread(long channelId, Guild guild) {
        ThreadChannel threadChannel = guild.getThreadChannelById(channelId);
        if (threadChannel == null) {
            logger.warn(
                    "Attempted to archive thread id: '{}' but could not find thread in guild: '{}'.",
                    channelId, guild.getName());
            return;
        }
        if (threadChannel.isArchived()) {
            logger.debug(
                    "Attempted to archive thread id: '{}' in guild: '{}' but thread is already closed.",
                    channelId, guild.getName());
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
