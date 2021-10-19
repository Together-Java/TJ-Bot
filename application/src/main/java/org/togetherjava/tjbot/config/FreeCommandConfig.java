package org.togetherjava.tjbot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
@JsonRootName(value = "freeCommand")
public final class FreeCommandConfig {
    private final long statusChannel;
    private final List<Long> monitoredChannels;

    /**
     * //todo add java docs, include required json format to configure this
     *
     * @param statusChannel
     * @param monitoredChannels
     */
    @JsonCreator
    private FreeCommandConfig(@JsonProperty("statusChannel") long statusChannel,
            @JsonProperty("monitoredChannels") long[] monitoredChannels) {
        this.statusChannel = statusChannel;
        this.monitoredChannels = Arrays.stream(monitoredChannels).boxed().toList();
    }

    /**
     * Gets the channelID where the status message will be displayed.
     *
     * @return the channelID for the status message
     */
    public long getStatusChannel() {
        return statusChannel;
    }

    /**
     *
     * @return
     */
    public Collection<Long> getMonitoredChannels() {
        return monitoredChannels;
    }
}
