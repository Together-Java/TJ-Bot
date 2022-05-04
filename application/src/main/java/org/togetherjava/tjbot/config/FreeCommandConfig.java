package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Config instance for the Free Command System see
 * {@link org.togetherjava.tjbot.commands.free.FreeCommand}
 *
 * The Json looks as follows:
 * 
 * <pre>
 * "freeCommand": [
 *   {
 *       "inactiveChannelDuration": duration,
 *       "messageRetrieveLimit": int_number,
 *       "statusChannel": long_number,
 *       "monitoredChannels": [long_number, long_number]
 *   }]
 * </pre>
 * 
 * Additional Guilds may add their settings by adding additional {@code {"statusChannel": ... } }
 *
 * The long channel ID can be found by right-clicking on the channel and selecting 'Copy ID'
 */
@SuppressWarnings("ClassCanBeRecord")
@JsonRootName("freeCommand")
public final class FreeCommandConfig {
    private final long statusChannel;
    private final List<Long> monitoredChannels;
    private final Duration inactiveChannelDuration;
    private final int messageRetrieveLimit;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private FreeCommandConfig(@JsonProperty("statusChannel") long statusChannel,
            @JsonProperty("monitoredChannels") List<Long> monitoredChannels,
            @JsonProperty("inactiveChannelDuration") Duration inactiveChannelDuration,
            @JsonProperty("messageRetrieveLimit") int messageRetrieveLimit) {
        this.statusChannel = statusChannel;
        this.monitoredChannels = Collections.unmodifiableList(monitoredChannels);
        this.messageRetrieveLimit = messageRetrieveLimit;
        this.inactiveChannelDuration = inactiveChannelDuration;
    }

    /**
     * Retrieves the channelID where the status message will be displayed.
     *
     * @return the Channel ID where the Status Message is expected to be displayed
     */
    public long getStatusChannel() {
        return statusChannel;
    }

    /**
     * Retrieves a Collection of the channels that this guild wants to have registered for
     * monitoring by the free/busy command system
     *
     * @return an Unmodifiable List of Channel ID's
     */
    public @NotNull Collection<Long> getMonitoredChannels() {
        return monitoredChannels; // already unmodifiable
    }

    /**
     * Gets the duration of inactivity after which a channel is considered inactive.
     * 
     * @return inactivity duration
     */
    public @NotNull Duration getInactiveChannelDuration() {
        return inactiveChannelDuration;
    }

    /**
     * Gets the limit of messages to retrieve when searching for previous status messages.
     * 
     * @return the message retrieve limit
     */
    public int getMessageRetrieveLimit() {
        return messageRetrieveLimit;
    }
}
