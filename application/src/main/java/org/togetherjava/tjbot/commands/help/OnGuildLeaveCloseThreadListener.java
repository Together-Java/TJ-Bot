package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.AddHelpChannel;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Remove all thread channels associated to a user when they leave the guild
 */
public class OnGuildLeaveCloseThreadListener implements EventReceiver {
    private static final Logger logger =
            LoggerFactory.getLogger(OnGuildLeaveCloseThreadListener.class);
    private final Database database;

    public OnGuildLeaveCloseThreadListener(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildMemberRemoveEvent leaveEvent) {
            onGuildMemberRemoved(leaveEvent);
        }
    }

    private void onGuildMemberRemoved(GuildMemberRemoveEvent leaveEvent) {
        ThreadChannel threadChannel;
        Set<Long> channelId = getThreadsAssociatedToLeaver(leaveEvent);
        if (channelId.isEmpty()) {
            logger.warn("Failed to get channelID user was associated with");
        } else {
            for (Long channel : channelId) {
                threadChannel = leaveEvent.getGuild().getThreadChannelById(channel);
                if (threadChannel == null) {
                    logger.warn("Thread channel ID: '{}' is already deleted.", channel);
                } else {
                    threadChannel.delete().queue();
                }
            }
        }
    }

    public Set<Long> getThreadsAssociatedToLeaver(GuildMemberRemoveEvent leaveEvent) {
        return database
            .readTransaction(context -> context.select(AddHelpChannel.ADD_HELP_CHANNEL.CHANNEL_ID))
            .from(AddHelpChannel.ADD_HELP_CHANNEL)
            .where(AddHelpChannel.ADD_HELP_CHANNEL.USER_ID.eq(leaveEvent.getUser().getIdLong()))
            .fetch()
            .stream()
            .map(databaseId -> databaseId.getValue(AddHelpChannel.ADD_HELP_CHANNEL.CHANNEL_ID))
            .collect(Collectors.toSet());

    }
}
