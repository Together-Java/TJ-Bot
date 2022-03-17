package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.util.*;
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
final class ChannelMonitor {
    // Map to store channel ID's, use Guild.getChannels() to guarantee order for display
    private final Map<Long, ChannelStatus> channelsToMonitorById;
    private final Map<Long, Long> guildIdToStatusChannel;

    ChannelMonitor() {
        guildIdToStatusChannel = new HashMap<>(); // JDA required to populate map
        channelsToMonitorById = new HashMap<>();
    }

    /**
     * Method for adding channels that need to be monitored.
     *
     * @param channelId the id of the channel to monitor
     */
    public void addChannelToMonitor(final long channelId) {
        channelsToMonitorById.put(channelId, new ChannelStatus(channelId));
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
        guildIdToStatusChannel.put(channel.getGuild().getIdLong(), channel.getIdLong());
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
        return guildIdToStatusChannel.containsKey(guildId);
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
        return channelsToMonitorById.containsKey(channelId);
    }

    private ChannelStatus requiresIsMonitored(final long channelId) {
        if (!channelsToMonitorById.containsKey(channelId)) {
            throw new IllegalArgumentException(
                    "Channel with id: %s is not monitored by free channel".formatted(channelId));
        }
        return channelsToMonitorById.get(channelId);
    }

    /**
     * This method tests if channel status to busy, see {@link ChannelStatus#isBusy()} for details.
     *
     * @param channelId the id for the channel to test.
     * @return {@code true} if the channel is 'busy', false if the channel is 'free'.
     * @throws IllegalArgumentException if the channel passed is not monitored. See
     *         {@link #addChannelToMonitor(long)}
     */
    public boolean isChannelBusy(final long channelId) {
        return requiresIsMonitored(channelId).isBusy();
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
     *         recently than the configured duration.
     * @throws IllegalArgumentException if the channel passed is not monitored. See
     *         {@link #addChannelToMonitor(long)}
     */
    public boolean isChannelInactive(@NotNull final TextChannel channel) {
        requiresIsMonitored(channel.getIdLong());

        // TODO change the entire inactive test to work via rest-actions
        return FreeUtil.getLastMessageId(channel)
            // black magic to convert OptionalLong into Optional<Long> because OptionalLong does not
            // have .map
            .stream()
            .boxed()
            .findFirst()
            .map(FreeUtil::timeFromId)
            .map(createdTime -> createdTime.isBefore(FreeUtil.inactiveTimeLimit()))
            .orElse(true); // if no channel history could be fetched assume channel is free
    }

    /**
     * This method sets the channel's status to 'busy' see {@link ChannelStatus#setBusy(long)} for
     * details.
     * 
     * @param channelId the id for the channel status to modify.
     * @param userId the id of the user changing the status to busy.
     * @throws IllegalArgumentException if the channel passed is not monitored. See
     *         {@link #addChannelToMonitor(long)}
     */
    public void setChannelBusy(final long channelId, final long userId) {
        requiresIsMonitored(channelId).setBusy(userId);
    }

    /**
     * This method sets the channel's status to 'free', see {@link ChannelStatus#setFree()} for
     * details.
     * 
     * @param channelId the id for the channel status to modify.
     * @throws IllegalArgumentException if the channel passed is not monitored. See
     *         {@link #addChannelToMonitor(long)}
     */
    public void setChannelFree(final long channelId) {
        requiresIsMonitored(channelId).setFree();
    }

    /**
     * This method provides a stream of the id's for guilds that are currently being monitored. This
     * is streamed purely as a simple method of encapsulation.
     *
     * @return a stream of guild id's
     */
    public @NotNull Stream<Long> guildIds() {
        return guildIdToStatusChannel.keySet().stream();
    }

    /**
     * This method provides a stream of the id's for channels where statuses are displayed. This is
     * streamed purely as a simple method of encapsulation.
     * 
     * @return a stream of channel id's
     */
    public @NotNull Stream<Long> statusIds() {
        return guildIdToStatusChannel.values().stream();
    }

    private @NotNull List<ChannelStatus> guildMonitoredChannelsList(@NotNull final Guild guild) {
        return guild.getChannels()
            .stream()
            .map(GuildChannel::getIdLong)
            .filter(channelsToMonitorById::containsKey)
            .map(channelsToMonitorById::get)
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
        StringJoiner content = new StringJoiner("\n");
        String categoryName = "";
        for (ChannelStatus status : statusFor) {
            TextChannel channel = guild.getTextChannelById(status.getChannelId());
            if (channel == null) {
                // pointless ... added to remove warnings
                continue;
            }
            Category category = channel.getParentCategory();
            if (category != null && !category.getName().equals(categoryName)) {
                categoryName = category.getName();
                // append the category name on a new line with markup for underlining
                // TODO possible bug when not all channels are part of categories, may mistakenly
                // include uncategorized channels inside previous category. will an uncategorized
                // channel return an empty string or null? javadocs don't say.
                content.add("\n__" + categoryName + "__");
            }
            content.add(status.toDiscordContentRaw());
        }

        return content.toString();
    }

    /**
     * This method checks all channels in a guild that are currently being monitored and are busy
     * and determines if the last time it was updated is more recent than the configured time see
     * {@link org.togetherjava.tjbot.config.FreeCommandConfig#INACTIVE_UNIT}. If so it changes the
     * channel's status to free, see {@link ChannelMonitor#isChannelInactive(TextChannel)}.
     * <p>
     * This method is run automatically during startup and should be run on a set schedule, as
     * defined in {@link org.togetherjava.tjbot.config.FreeCommandConfig}. The scheduled execution
     * is not currently implemented
     * 
     * @param guild the guild for which to test the channel statuses of.
     */
    public void updateStatusFor(@NotNull Guild guild) {
        // TODO add automation after Routine support (#235) is pushed
        guildMonitoredChannelsList(guild).parallelStream()
            .filter(ChannelStatus::isBusy)
            .map(ChannelStatus::getChannelId)
            .map(guild::getTextChannelById)
            .filter(Objects::nonNull) // pointless, added for warnings
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
     * @throws IllegalArgumentException if the guild passed has not configured in the free command
     *         system, see {@link #addChannelForStatus(TextChannel)}
     */
    public @NotNull TextChannel getStatusChannelFor(@NotNull final Guild guild) {
        if (!guildIdToStatusChannel.containsKey(guild.getIdLong())) {
            throw new IllegalArgumentException(
                    "Guild %s is not configured in the free command system."
                        .formatted(guild.getName()));
        }

        long channelId = guildIdToStatusChannel.get(guild.getIdLong());
        TextChannel channel = guild.getTextChannelById(channelId);

        if (channel == null) {
            throw new IllegalStateException("Status channel %d does not exist in guild %s"
                .formatted(channelId, guild.getName()));
        }

        return channel;
    }

    /**
     * The toString method for this class, it generates a human-readable text string of the
     * currently monitored channels and the channels the status are printed in.
     * 
     * @return the human-readable text string that describes this class.
     */
    @Override
    public String toString() {
        // This is called on boot as a debug level message by the logger
        return "Monitoring Channels: %s%nDisplaying on Channels: %s"
            .formatted(channelsToMonitorById, guildIdToStatusChannel);
    }
}
