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
 * Additional Guilds may add their settings by adding additional {"statusChannel....... }
 *
 * The long channel ID can be found by right-clicking on the channel and selecting 'Copy ID'
 */
@SuppressWarnings("ClassCanBeRecord")
@JsonRootName(value = "freeCommand")
public final class FreeCommandConfig {
    public static final long INACTIVE_DURATION = 1;
    public static final ChronoUnit INACTIVE_UNIT = ChronoUnit.HOURS;
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
     * Retrieves a List of the channels on this guild that the guild wants monitored by the
     * free/busy command system
     *
     * @return an Unmodifiable List of Channel ID's
     */
    public @NotNull Collection<Long> getMonitoredChannels() {
        return monitoredChannels; // already unmodifiable
    }
}
