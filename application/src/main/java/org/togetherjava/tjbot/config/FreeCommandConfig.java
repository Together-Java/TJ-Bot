package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.jetbrains.annotations.NotNull;

import java.time.temporal.ChronoUnit;
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
    // TODO make constants configurable via config file once config templating (#234) is pushed
    public static final long INACTIVE_DURATION = 1;
    public static final ChronoUnit INACTIVE_UNIT = ChronoUnit.HOURS;
    public static final long INACTIVE_TEST_INTERVAL = 15;
    public static final ChronoUnit INACTIVE_TEST_UNIT = ChronoUnit.MINUTES;
    public static final int MESSAGE_RETRIEVE_LIMIT = 10;

    private final long statusChannel;
    private final List<Long> monitoredChannels;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private FreeCommandConfig(@JsonProperty("statusChannel") long statusChannel,
            @JsonProperty("monitoredChannels") List<Long> monitoredChannels) {
        this.statusChannel = statusChannel;
        this.monitoredChannels = Collections.unmodifiableList(monitoredChannels);
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
}
