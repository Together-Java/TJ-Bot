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
public final class ChannelStatus {
    public static final boolean FREE = false;
    public static final boolean BUSY = true;

    private static final String FREE_STATUS = ":green_circle:";
    private static final String BUSY_STATUS = ":red_circle:";

    private final long channelId;
    private long userId;
    private volatile boolean isBusy;
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
    ChannelStatus(long id) {
        channelId = id;
        isBusy = BUSY;
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
    public boolean isBusy() {
        return isBusy;
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
     * @return The currently stored name of the channel, originally the long id as string.
     */
    public @NotNull String getName() {
        return name;
    }


    private void setName(@NotNull String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Method used to keep the channel name up to date with the {@link JDA}. This method is not
     * called automatically. Manually update before using the value.
     * <p>
     * The recommended value to use is {@link TextChannel#getAsMention()}
     * <p>
     * This method is called in multithreaded context, however the value is not expected to change
     * regularly and will not break anything if its invalid so no has not been made thread safe.
     *
     * @param guild the {@link Guild} that the channel belongs to, to retrieve its name from.
     */
    public void updateChannelName(@NotNull Guild guild) {
        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException(
                    "The guild passed in '%s' is not related to the channel this status is for: %s"
                        .formatted(guild.getName(), this));
        }
        if (channel instanceof TextChannel textChannel) {
            setName(textChannel.getAsMention());
        } else
            throw new IllegalStateException(
                    "This channel status was created with the id for a non-text-channel and status cannot be monitored: '%s'"
                        .formatted(channelId));
    }

    public void setBusy(long userId) {
        if (isBusy == FREE) {
            this.isBusy = BUSY;
            this.userId = userId;
        }
    }

    public boolean isAsker(long userId) {
        return this.userId == userId;
    }

    public void setFree() {
        if (isBusy == BUSY) {
            isBusy = FREE;
        }
    }

    // todo should I overload equals with equals(long) so that a Set may be used instead of a Map
    /**
     * The identity of this object of solely based on the id value. Compares the long id's and
     * determines if they are equal.
     * 
     * @param o the other object to test against
     * @return whether the objects have the same id or not.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ChannelStatus status = (ChannelStatus) o;
        return channelId == status.channelId;
    }

    /**
     * A String representation of the instance, gives the name and the current status.
     *
     * @return a String representation of the instance.
     */
    @Override
    public String toString() {
        return "ChannelStatus{ %s is %s }".formatted(name, isBusy ? "busy" : "not busy");
    }

    /**
     * A {@link #toString()} method specially formatted for Discord ({@link JDA}. Uses emojis by
     * string representation, that discord will automatically convert into images. Using this string
     * outside of discord will display unexpected results.
     *
     * @return a String representation of ChannelStatus, formatted for Discord
     */
    public String toDiscord() {
        return "%s %s".formatted(isBusy ? BUSY_STATUS : FREE_STATUS, name);
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
