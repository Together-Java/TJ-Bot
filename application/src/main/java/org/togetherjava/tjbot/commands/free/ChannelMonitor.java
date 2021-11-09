package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


/**
 * A class responsible for monitoring the status of channels and reporting on their busy/free status
 * for use by {@link FreeCommand}.
 *
 * Channels for monitoring are added via {@link #addChannelToMonitor(long)} however the monitoring
 * will not be accessible/visible until a channel in the same {@link Guild} is registered for the
 * output via {@link #addChannelForStatus(TextChannel)}. This will all happen automatically for any
 * channels listed in {@link org.togetherjava.tjbot.config.FreeCommandConfig}.
 *
 * When a status channel is added for a guild, all monitored channels for that guild are tested and
 * an {@link IllegalStateException} is thrown if any of them are not {@link TextChannel}s.
 *
 * After successful configuration, any changes in busy/free status will automatically be displayed
 * in the configured {@code Status Channel} for that guild.
 */
public final class ChannelMonitor {
    // Map to store channel ID's, use Guild.getChannels() to guarantee order for display
    private final Map<Long, ChannelStatus> channelsToMonitor;
    private final Map<Long, Long> postStatusInChannel;

    ChannelMonitor() {
        postStatusInChannel = new HashMap<>(); // JDA required to populate map
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
     * This method also calls a method which updates the status of the channels in the
     * {@link Guild}. So always add the status channel <strong>after</strong> you have added all
     * monitored channels for the guild, see {@link #addChannelToMonitor(long)}.
     *
     * @param channel the channel the status message must be displayed in
     */
    public void addChannelForStatus(@NotNull final TextChannel channel) {
        postStatusInChannel.put(channel.getGuild().getIdLong(), channel.getIdLong());
        updateStatusFor(channel.getGuild());
    }

    /**
     * This method tests whether a guild id is configured for monitoring in the free command system.
     * To add a guild for monitoring see {@link org.togetherjava.tjbot.config.FreeCommandConfig} or
     * {@link #addChannelForStatus(TextChannel)}.
     * 
     * @param guildId the id of the guild to test.
     * @return whether the guild is configured in the free command system or not.
     */
    public boolean isMonitoringGuild(final long guildId) {
        return postStatusInChannel.containsKey(guildId);
    }

    /**
     * This method tests whether a channel id is configured for monitoring in the free command
     * system. To add a channel for monitoring see
     * {@link org.togetherjava.tjbot.config.FreeCommandConfig} or
     * {@link #addChannelToMonitor(long)}.
     * 
     * @param channelId the id of the channel to test.
     * @return {@code true} if the channel is configured in the system, {@code false} otherwise.
     */
    public boolean isMonitoringChannel(final long channelId) {
        return channelsToMonitor.containsKey(channelId);
    }

    /**
     * This is a delegation method to pass operations to {@link ChannelStatus}. see
     * {@link ChannelStatus#isBusy()} for details.
     *
     * @param channelId the id for the channel to test.
     * @return {@code true} if the channel is 'busy', false if the channel is 'free'.
     */
    public boolean isChannelBusy(final long channelId) {
        return channelsToMonitor.get(channelId).isBusy();
    }

    /**
     * This method tests if a channel is currently active by fetching the latest message and testing
     * if it was posted more recently than the configured time limit, see
     * {@link FreeUtil#inactiveTimeLimit()} and
     * {@link org.togetherjava.tjbot.config.FreeCommandConfig#INACTIVE_DURATION},
     * {@link org.togetherjava.tjbot.config.FreeCommandConfig#INACTIVE_UNIT}.
     *
     * @param channel the channel to test.
     * @return {@code true} if the channel is inactive, false if it has received messages more
     *         recently than an hour ago.
     */
    public boolean isChannelInactive(@NotNull final TextChannel channel) {
        if (!channelsToMonitor.containsKey(channel.getIdLong())) {
            throw new IllegalArgumentException(
                    "Channel requested %s is not monitored by free channel"
                        .formatted(channel.getName()));
        }
        // TODO change the entire inactive test to work via restactions
        return channel.getHistory()
            .retrievePast(1)
            .map(messages -> messages.get(0))
            .map(Message::getTimeCreated)
            .map(createdTime -> createdTime.isBefore(FreeUtil.inactiveTimeLimit()))
            .complete();
    }

    /**
     * This is a delegation method to pass operations to {@link ChannelStatus}. see
     * {@link ChannelStatus#setBusy(long)} for details.
     * 
     * @param channelId the id for the channel status to modify.
     * @param userId the id of the user changing the status to busy.
     */
    public void setChannelBusy(final long channelId, final long userId) {
        channelsToMonitor.get(channelId).setBusy(userId);
    }

    /**
     * This is a delegation method to pass operations to {@link ChannelStatus}. see
     * {@link ChannelStatus#setFree()} for details.
     * 
     * @param channelId the id for the channel status to modify.
     */
    public void setChannelFree(final long channelId) {
        channelsToMonitor.get(channelId).setFree();
    }

    /**
     * This method provides a stream of the id's for guilds that are currently being monitored. This
     * is streamed purely as a simple method of encapsulation.
     *
     * @return a stream of guild id's
     */
    public @NotNull Stream<Long> guildIds() {
        return postStatusInChannel.keySet().stream();
    }

    /**
     * This method provides a stream of the id's for channels that status's are displayed in. This
     * is streamed purely as a simple method of encapsulation.
     * 
     * @return a stream of channel id's
     */
    public @NotNull Stream<Long> statusIds() {
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

    /**
     * Creates the status message (specific to the guild specified) that shows which channels are
     * busy/free.
     * <p>
     * It first updates the channel names, order and grouping(categories) according to
     * {@link net.dv8tion.jda.api.JDA} for the monitored channels. So that the output is always
     * consistent with remote changes.
     * 
     * @param guild the guild the message is intended for.
     * @return a string representing the busy/free status of channels in this guild. The String
     *         includes emojis and other discord specific markup. Attempting to display this
     *         somewhere other than discord will lead to unexpected results.
     */
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
                // FIXME possible bug when not all channels are part of categories, may mistakenly
                // include uncategoried channels inside previous category. will an uncategoried
                // channel return an empty string or null? javadocs dont say.
                sb.append("\n__").append(categoryName).append("__\n");
            }
            sb.append(status.toDiscord()).append("\n");
        }

        return sb.toString();
    }

    /**
     * This method checks all channels in a guild that is currently being monitored and are
     * currently busy and determines if the last time it was updated is more than an hour ago. If so
     * it changes the channel's status to free.
     * <p>
     * This method is run automatically during startup and should be run on a 15minute schedule. The
     * scheduled execution is not currently implemented
     * 
     * @param guild the guild for which to test the channel statuses of.
     */
    public void updateStatusFor(@NotNull Guild guild) {
        guildMonitoredChannelsList(guild).parallelStream()
            .filter(ChannelStatus::isBusy)
            .map(ChannelStatus::getChannelId)
            .map(guild::getTextChannelById)
            .filter(this::isChannelInactive)
            .map(TextChannel::getIdLong)
            .forEach(this::setChannelFree);
    }

    /**
     * This method returns the {@link TextChannel} that has been configured as the output of the
     * status messages about busy/free for the specified guild.
     *
     * @param guild the {@link Guild} for which to retrieve the TextChannel for.
     * @return the TextChannel where status messages are output in the specified guild.
     */
    public @NotNull TextChannel getStatusChannelFor(@NotNull final Guild guild) {
        // TODO add error checking for invalid keys ??
        return guild.getTextChannelById(postStatusInChannel.get(guild.getIdLong()));
    }

    /**
     * The toString method for this class, it generates a human-readable text string of the
     * currently monitored channels and the channels the status are printed in.
     * 
     * @return the human-readable text string that describes this class.
     */
    @Override
    public String toString() {
        // This is called on boot by as a debug level logger
        return "Monitoring Channels: %s%nDisplaying on Channels: %s".formatted(channelsToMonitor,
                postStatusInChannel);
    }
}
