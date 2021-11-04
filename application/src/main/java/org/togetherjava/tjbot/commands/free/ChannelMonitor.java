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
    public void addChannelToMonitor(final long channelId) {
        channelsToMonitor.put(channelId, new ChannelStatus(channelId));
    }

    /**
     * Method for adding the channel that the status will be printed in. Even though the method only
     * stores the long id it requires, the method requires the actual {@link TextChannel} to be
     * passed because it needs to verify it as well as store the guild id.
     *
     * This method also calls a method which updates the status of the channels in the {@link Guild}
     * . So always add the status channel AFTER you have added all monitored channels for the guild,
     * see {@link #addChannelToMonitor(long)}.
     *
     * @param channel the channel the status message must be displayed in
     */
    public void addChannelForStatus(@NotNull final TextChannel channel) {
        postStatusInChannel.put(channel.getGuild().getIdLong(), channel.getIdLong());
        updateStatusFor(channel.getGuild());
    }

    public boolean isMonitoringGuild(final long guildId) {
        return postStatusInChannel.containsKey(guildId);
    }

    public boolean isMonitoringChannel(final long channelId) {
        return channelsToMonitor.containsKey(channelId);
    }

    public boolean isChannelBusy(final long channelId) {
        return channelsToMonitor.get(channelId).isBusy();
    }

    public boolean isChannelInactive(@NotNull final TextChannel channel) {
        if (!channelsToMonitor.containsKey(channel.getIdLong())) {
            throw new IllegalArgumentException(
                    "Channel requested %s is not monitored by free channel"
                        .formatted(channel.getName()));
        }
        return channel.getHistory()
            .retrievePast(1)
            .map(messages -> messages.get(0))
            .map(Message::getTimeCreated)
            .map(createdTime -> createdTime.isBefore(Util.anHourAgo()))
            .complete();
    }

    public void setChannelBusy(final long channelId, final long userId) {
        channelsToMonitor.get(channelId).setBusy(userId);
    }

    public void setChannelFree(final long channelId) {
        channelsToMonitor.get(channelId).setFree();
    }

    public Stream<Long> guildIds() {
        return postStatusInChannel.keySet().stream();
    }

    public Stream<Long> statusIds() {
        return postStatusInChannel.values().stream();
    }

    private @NotNull List<ChannelStatus> guildMonitoredChannelsList(@NotNull final Guild guild) {
        return guild.getChannels()
            .stream()
            .map(GuildChannel::getIdLong)
            .filter(channelsToMonitor::containsKey)
            .map(channelsToMonitor::get)
            .toList();
    }

    public String statusMessage(@NotNull final Guild guild) {
        List<ChannelStatus> statusFor = guildMonitoredChannelsList(guild);

        // update name so that current channel name is used
        statusFor.forEach(channelStatus -> channelStatus.updateChannelName(guild));

        // dynamically separate channels by channel categories
        StringBuilder sb = new StringBuilder();
        String categoryName = "";
        for (ChannelStatus status : statusFor) {
            Category category = guild.getGuildChannelById(status.getChannelId()).getParent();
            if (category != null && !category.getName().equals(categoryName)) {
                categoryName = category.getName();
                // append the category name on a new line with markup for underlining
                sb.append("\n__").append(categoryName).append("__\n");
            }
            sb.append(status.toDiscord()).append("\n");
        }

        return sb.toString();
    }

    public void updateStatusFor(@NotNull Guild guild) {
        List<ChannelStatus> statusFor = guildMonitoredChannelsList(guild);

        statusFor.stream()
            .parallel()
            .filter(ChannelStatus::isBusy)
            .map(ChannelStatus::getChannelId)
            .map(guild::getTextChannelById)
            .filter(this::isChannelInactive)
            .map(TextChannel::getIdLong)
            .forEach(this::setChannelFree);

    }


    public TextChannel getStatusChannelFor(@NotNull final Guild guild) {
        // todo add error checking for invalid keys ??
        return guild.getTextChannelById(postStatusInChannel.get(guild.getIdLong()));
    }

    @Override
    public String toString() {
        return "Monitoring Channels: %s%nDisplaying on Channels: %s".formatted(channelsToMonitor,
                postStatusInChannel);
    }
}
