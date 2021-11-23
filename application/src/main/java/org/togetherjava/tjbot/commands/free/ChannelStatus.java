package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Class that tracks the current free/busy status of a channel that requires monitoring.
 */
final class ChannelStatus {

    private final long channelId;
    private volatile long userId;
    private volatile ChannelStatusType status;
    private String name;

    /**
     * Creates an instance of a Channel Status.
     * <p>
     * This does not validate the id as that requires the JDA. Any ChannelStatus that gets created
     * with an invalid id *should* be ignored and won't be invoked. (Since the Channel statuses are
     * selected by retrieval of channels id's via the guild id, before retrieving the relevant
     * ChannelStatuses).
     * 
     * @param id the long id of the {@link net.dv8tion.jda.api.entities.TextChannel} to monitor.
     */
    ChannelStatus(final long id) {
        channelId = id;
        status = ChannelStatusType.BUSY;
        name = Long.toString(id);
    }

    /**
     * Retrieves whether the channel is currently busy/free.
     * <p>
     * This value is volatile but is not thread safe in any other way. While statuses change
     * frequently, each individual status instance *should* only be modified from a single source,
     * since it represents only a single channel and modification will only be triggered by activity
     * in that one channel.
     * 
     * @return the current stored status related to the channel id.
     */
    public synchronized boolean isBusy() {
        return status.isBusy();
    }

    /**
     * Method to test if an id is the same as the id of the help requester who most recently posted
     * a question.
     *
     * @param userId the id to test
     * @return {@code true} if the id value passed in is the same as the value of the user who most
     *         recently changed the status to 'busy'. {@code false} otherwise.
     */
    public boolean isAsker(final long userId) {
        return this.userId == userId;
    }

    /**
     * Retrieves the id for the {@link net.dv8tion.jda.api.entities.TextChannel} that this instance
     * represents. There is no guarantee that the id is valid according to the {@link JDA}.
     * 
     * @return the {@link net.dv8tion.jda.api.entities.TextChannel} id.
     */
    public long getChannelId() {
        return channelId;
    }

    /**
     * Retrieves the locally stored name of the {@link net.dv8tion.jda.api.entities.TextChannel}
     * this represents. This value is initialised to the channel id and as such is never null. The
     * name should first be set by retrieving the name the {@link JDA} currently uses, before
     * calling this.
     * <p>
     * The recommended value to use is {@link TextChannel#getAsMention()}.
     *
     * @return The currently stored name of the channel.
     */
    public @NotNull String getName() {
        return name;
    }

    private void setName(@NotNull final String name) {
        this.name = name;
    }

    /**
     * Method used to keep the channel name up to date with the {@link JDA}. This method is not
     * called automatically. Manually update before using the value.
     * <p>
     * The recommended value to use is {@link TextChannel#getAsMention()}
     * <p>
     * This method is called in multithreaded context, however the value is not expected to change
     * regularly and will not break anything if it is incorrect for a read or two, and it should be
     * updated before use, which will happen in the using thread. So it has not been made thread
     * safe.
     *
     * @param guild the {@link Guild} that the channel belongs to, to retrieve its name from.
     * @throws IllegalArgumentException if the guild has not been added, see
     *         {@link ChannelMonitor#addChannelForStatus(TextChannel)}
     * @throws IllegalStateException if a channel was added, see
     *         {@link ChannelMonitor#addChannelToMonitor(long)}, that is not a {@link TextChannel}.
     *         Since addChannelToMonitor does not access the {@link JDA} the entry can only be
     *         validated before use instead of on addition.
     */
    public void updateChannelName(@NotNull final Guild guild) {
        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException(
                    "The guild passed in '%s' is not related to the channel this status is for: %s"
                        .formatted(guild.getName(), this));
        }
        if (!(channel instanceof TextChannel textChannel)) {
            throw new IllegalStateException("This channel status was created with the id for a"
                    + "non-text-channel and status cannot be monitored: '%s'".formatted(channelId));
        } else {
            setName(textChannel.getAsMention());
        }
    }

    /**
     * Method to set the channel status to busy, a user id is passed in to keep track of the current
     * user requesting help. This id will be used to confirm that the author is satisfied with the
     * channel being marked as free.
     * <p>
     * This functionality is not yet implemented so the id can be anything atm. Also note that on
     * reboot the bot does not currently search for the author so the first time its marked as free
     * there will be no confirmation.
     * 
     * @param userId the id of the user who changed the status to 'busy'
     */
    public synchronized void setBusy(final long userId) {
        if (status.isFree()) {
            status = ChannelStatusType.BUSY;
            this.userId = userId;
        }
    }

    /**
     * Method to set the channel status to free, the user id of the previous help requester is not
     * overwritten by this method. So until another user changes the status to busy the old value
     * will remain.
     * <p>
     * The value will be 0 until the first time that the status is changed from free to busy.
     * <p>
     * This functionality is not yet implemented so the id can be anything atm.
     */
    public synchronized void setFree() {
        status = ChannelStatusType.FREE;
    }

    /**
     * The identity of this object is solely based on the id value. Compares the long id's and
     * determines if they are equal.
     * 
     * @param o the other object to test against
     * @return whether the objects have the same id or not.
     */
    @Override
    public boolean equals(final Object o) {
        // TODO should I overload equals with equals(long) so that a Set may be used instead of a
        // Map
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ChannelStatus channelStatus = (ChannelStatus) o;
        return channelId == channelStatus.channelId;
    }

    /**
     * A String representation of the instance, gives the name and the current status.
     *
     * @return a String representation of the instance.
     */
    @Override
    public @NotNull String toString() {
        return "ChannelStatus{ %s is %s }".formatted(name, status.description());
    }

    /**
     * A {@link #toString()} method specially formatted for Discord ({@link JDA}. Uses emojis by
     * string representation, that discord will automatically convert into images. Using this string
     * outside of discord will display unexpected results.
     *
     * @return a String representation of ChannelStatus, formatted for Discord
     */
    public @NotNull String toDiscordContentRaw() {
        return "%s %s".formatted(status.toDiscordContentRaw(), name);
    }

    /**
     * The hash that represents the instance. It is based only on the id value.
     *
     * @return the instance's hash.
     */
    @Override
    public int hashCode() {
        return Objects.hash(channelId);
    }
}
