package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


/**
 * A class responsible for monitoring the status of channels and reporting on their busy/free status
 * for use by {@link FreeCommand}
 */
public class ChannelMonitor {
    // Map to store channel ID's, use Guild.getChannels() to guarantee order for display
    private final Map<Long, ChannelStatus> channelsToMonitor;
    private final Map<Long, Long> postStatusInChannel;



    ChannelMonitor() {
        postStatusInChannel = new HashMap<>(); // JDA required to fetch guildID
        channelsToMonitor = new HashMap<>();
    }

    /**
     * Method for adding channels that need to be monitored.
     *
     * @param channelId the id of the channel to monitor
     */
    public void addChannelToMonitor(long channelId) {
        channelsToMonitor.put(channelId, new ChannelStatus(channelId));
    }

    /**
     * Method for adding the channel that the status will be printed in. Even though the method only
     * stores the long id it requires, the method requires the actual {@link TextChannel} to be
     * passed because it needs to verify it as well as store the guild id.
     *
     * @param channel the channel the status message must be displayed in
     */
    public void addChannelForStatus(TextChannel channel) {
        postStatusInChannel.put(channel.getGuild().getIdLong(), channel.getIdLong());
        updateChannelStatusFor(channel.getGuild());
    }

    public boolean isMonitoringGuild(long guildId) {
        return postStatusInChannel.containsKey(guildId);
    }

    public boolean isChannelBusy(long channelId) {
        return channelsToMonitor.get(channelId).isBusy();
    }

    public Stream<Long> guildIds() {
        return postStatusInChannel.keySet().stream();
    }

    public Stream<Long> statusIds() {
        return postStatusInChannel.values().stream();
    }

    public boolean isMonitoringChannel(long channelId) {
        return channelsToMonitor.containsKey(channelId);
    }

    public void setChannelBusy(long channelId, long userId) {
        channelsToMonitor.get(channelId).setBusy(userId);
    }

    public String statusMessage(@NotNull Guild guild) {
        List<ChannelStatus> statusFor = guild.getChannels()
            .stream()
            .map(GuildChannel::getIdLong)
            .filter(channelsToMonitor::containsKey)
            .map(channelsToMonitor::get)
            .toList();

        // update name so that current channel name is used
        statusFor.forEach(channelStatus -> channelStatus
            .setName(guild.getGuildChannelById(channelStatus.getChannelId()).getAsMention()));

        // dynamically separate channels by channel categories
        StringBuilder sb = new StringBuilder();
        String categoryName = "";
        for (ChannelStatus status : statusFor) {
            Category category = guild.getGuildChannelById(status.getChannelId()).getParent();
            if (category != null && !category.getName().equals(categoryName)) {
                categoryName = category.getName();
                sb.append("\n__").append(categoryName).append("__\n");
            }
            sb.append(status.toDiscord()).append("\n");
        }

        return sb.toString();
    }

    private void updateChannelStatusFor(@NotNull Guild guild) {
        //need to complete code here
    }

    public TextChannel getStatusChannelFor(@NotNull final Guild guild) {
        // todo add error checking for invalid keys ??
        return guild.getTextChannelById(postStatusInChannel.get(guild.getIdLong()));
    }

    public String toString() {
        return "Monitoring Channels: %s%nDisplaying on Channels: %s".formatted(channelsToMonitor,
                postStatusInChannel);
    }
}
