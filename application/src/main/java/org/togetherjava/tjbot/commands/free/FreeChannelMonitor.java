package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.FreeCommandConfig;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.stream.LongStream;
import java.util.stream.Stream;


/**
 * A class responsible for monitoring the status of channels and reporting on their busy/free status
 * for use by {@link FreeCommand}.
 * <p>
 * Channels for monitoring are added via {@link #addChannelToMonitor(long)} however the monitoring
 * will not be accessible/visible until a channel in the same {@link Guild} is registered for the
 * output via {@link #addChannelForStatus(TextChannel)}. This will all happen automatically for any
 * channels listed in {@link org.togetherjava.tjbot.config.FreeCommandConfig}.
 * <p>
 * When a status channel is added for a guild, all monitored channels for that guild are tested and
 * an {@link IllegalStateException} is thrown if any of them are not {@link TextChannel}s.
 * <p>
 * After successful configuration, any changes in busy/free status will automatically be displayed
 * in the configured {@code Status Channel} for that guild.
 */
public final class FreeChannelMonitor {
    // Map to store channel ID's, use Guild.getChannels() to guarantee order for display
    private final Map<Long, ChannelStatus> channelsToMonitorById;
    private final Map<Long, Long> guildIdToStatusChannel;
    private final Map<Long, Long> channelIdToMessageIdForStatus;

    private static final String STATUS_TITLE = "**__CHANNEL STATUS__**\n\n";
    private static final Color MESSAGE_HIGHLIGHT_COLOR = Color.decode("#CCCC00");

    private final Config config;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     */
    public FreeChannelMonitor(@NotNull Config config) {
        guildIdToStatusChannel = new HashMap<>(); // JDA required to populate map
        channelsToMonitorById = new HashMap<>();
        channelIdToMessageIdForStatus = new HashMap<>();
        this.config = config;
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
     * <p>
     * This method also calls a method which updates the status of the channels in the
     * {@link Guild}. So always add the status channel <strong>after</strong> you have added all
     * monitored channels for the guild, see {@link #addChannelToMonitor(long)}.
     *
     * @param channel the channel the status message must be displayed in
     */
    public void addChannelForStatus(@NotNull final TextChannel channel) {
        guildIdToStatusChannel.put(channel.getGuild().getIdLong(), channel.getIdLong());
        freeInactiveChannels(channel.getGuild());
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
     * if it was posted more recently than the configured time limit.
     *
     * @param channel the channel to test.
     * @param when the reference moment, usually "now"
     * @return {@code true} if the channel is inactive, false if it has received messages more
     *         recently than the configured duration.
     * @throws IllegalArgumentException if the channel passed is not monitored. See
     *         {@link #addChannelToMonitor(long)}
     */
    public boolean isChannelInactive(@NotNull final TextChannel channel, @NotNull Instant when) {
        requiresIsMonitored(channel.getIdLong());

        OptionalLong maybeLastMessageId = FreeUtil.getLastMessageId(channel);
        if (maybeLastMessageId.isEmpty()) {
            return true;
        }

        FreeCommandConfig configForChannel = config.getFreeCommandConfig()
            .stream()
            .filter(freeConfig -> freeConfig.getMonitoredChannels().contains(channel.getIdLong()))
            .findAny()
            .orElseThrow();

        return TimeUtil.getTimeCreated(maybeLastMessageId.orElseThrow())
            .toInstant()
            .isBefore(when.minus(configForChannel.getInactiveChannelDuration()));
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
     * Gets a stream with IDs of all monitored channels that are currently marked busy.
     *
     * @return stream with IDs of all busy channels
     */
    public LongStream getBusyChannelIds() {
        return channelsToMonitorById.values()
            .stream()
            .filter(ChannelStatus::isBusy)
            .mapToLong(ChannelStatus::getChannelId);
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
     * and determines if the last time it was updated is more recent than the configured time. If so
     * it changes the channel's status to free, see
     * {@link FreeChannelMonitor#isChannelInactive(TextChannel, Instant)}.
     * <p>
     * This method is run automatically during startup and on a set schedule, as defined in
     * {@link org.togetherjava.tjbot.config.FreeCommandConfig}.
     *
     * @param guild the guild for which to test the channel statuses of.
     * @return all inactive channels that have been updated
     */
    public @NotNull Collection<TextChannel> freeInactiveChannels(@NotNull Guild guild) {
        Instant now = Instant.now();

        List<TextChannel> inactiveChannels = guildMonitoredChannelsList(guild).parallelStream()
            .filter(ChannelStatus::isBusy)
            .map(ChannelStatus::getChannelId)
            .map(guild::getTextChannelById)
            .filter(Objects::nonNull) // pointless, added for warnings
            .filter(busyChannel -> isChannelInactive(busyChannel, now))
            .toList();

        inactiveChannels.stream().map(TextChannel::getIdLong).forEach(this::setChannelFree);

        return inactiveChannels;
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
     * Displays the message that will be displayed for users.
     * <p>
     * This method detects if any messages have been posted in the channel below the status message.
     * If that is the case this will delete the existing status message and post another one so that
     * it's the last message in the channel.
     * <p>
     * If it cannot find an existing status message it will create a new one.
     * <p>
     * Otherwise it will edit the existing message.
     *
     * @param guild the guild to display the status in.
     */
    public void displayStatus(@NotNull Guild guild) {
        TextChannel channel = getStatusChannelFor(guild);

        String messageTxt = buildStatusMessage(guild);
        MessageEmbed embed = new EmbedBuilder().setTitle(STATUS_TITLE)
            .setDescription(messageTxt)
            .setFooter(channel.getJDA().getSelfUser().getName())
            .setTimestamp(Instant.now())
            .setColor(MESSAGE_HIGHLIGHT_COLOR)
            .build();

        getStatusMessageIn(channel).flatMap(this::deleteIfNotLatest)
            .ifPresentOrElse(message -> message.editMessageEmbeds(embed).queue(),
                    () -> channel.sendMessageEmbeds(embed)
                        .queue(message -> channelIdToMessageIdForStatus.put(channel.getIdLong(),
                                message.getIdLong())));
    }

    private @NotNull Optional<Message> deleteIfNotLatest(@NotNull Message message) {
        OptionalLong lastId = FreeUtil.getLastMessageId(message.getTextChannel());
        if (lastId.isPresent() && lastId.getAsLong() != message.getIdLong()) {
            message.delete().queue();
            return Optional.empty();
        }

        return Optional.of(message);
    }

    private @NotNull Optional<Message> getStatusMessageIn(@NotNull TextChannel channel) {
        if (!channelIdToMessageIdForStatus.containsKey(channel.getIdLong())) {
            return findExistingStatusMessage(channel);
        }
        return Optional.ofNullable(channelIdToMessageIdForStatus.get(channel.getIdLong()))
            .map(channel::retrieveMessageById)
            .map(RestAction::complete);
    }

    private @NotNull Optional<Message> findExistingStatusMessage(@NotNull TextChannel channel) {
        // will only run when bot starts, afterwards its stored in a map

        FreeCommandConfig configForChannel = config.getFreeCommandConfig()
            .stream()
            .filter(freeConfig -> freeConfig.getStatusChannel() == channel.getIdLong())
            .findAny()
            .orElseThrow();

        Optional<Message> statusMessage = FreeUtil
            .getChannelHistory(channel, configForChannel.getMessageRetrieveLimit())
            .flatMap(history -> history.stream()
                .filter(message -> !message.getEmbeds().isEmpty())
                .filter(message -> message.getAuthor().equals(channel.getJDA().getSelfUser()))
                // TODO the equals is not working, i believe its because there is no getTitleRaw()
                // .filter(message -> STATUS_TITLE.equals(message.getEmbeds().get(0).getTitle()))
                .findFirst());

        channelIdToMessageIdForStatus.put(channel.getIdLong(),
                statusMessage.map(Message::getIdLong).orElse(null));
        return statusMessage;
    }

    /**
     * Method for creating the message that shows the channel statuses for the specified guild.
     * <p>
     * This method dynamically builds the status message as per the current values on the guild,
     * including the channel categories. This method will detect any changes made on the guild and
     * represent those changes in the status message.
     *
     * @param guild the guild that the message is required for.
     * @return the message to display showing the channel statuses. Includes Discord specific
     *         formatting, trying to display elsewhere may have unpredictable results.
     * @throws IllegalArgumentException if the guild passed in is not configured in the free command
     *         system, see {@link FreeChannelMonitor#addChannelForStatus(TextChannel)}.
     */
    public @NotNull String buildStatusMessage(@NotNull Guild guild) {
        if (!isMonitoringGuild(guild.getIdLong())) {
            throw new IllegalArgumentException(
                    "The guild '%s(%s)' is not configured in the free command system"
                        .formatted(guild.getName(), guild.getIdLong()));
        }

        return statusMessage(guild);
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
