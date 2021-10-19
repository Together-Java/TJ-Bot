package org.togetherjava.tjbot.commands.free;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ChannelStatus {
    public static final boolean FREE = false;
    public static final boolean BUSY = true;

    private static final String FREE_STATUS = ":green_circle:";
    private static final String BUSY_STATUS = ":red_circle:";

    private final long channelID;
    private boolean isBusy;
    private String name;

    protected ChannelStatus(long id) {
        channelID = id;
        isBusy = true;
        name = Long.toString(id);
    }

    public boolean isBusy() {
        return isBusy;
    }

    public long getChannelID() {
        return channelID;
    }

    public @NotNull String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    // todo make threadsafe?
    protected void busy(boolean isBusy) {
        this.isBusy = isBusy;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChannelStatus that = (ChannelStatus) o;
        return channelID == that.channelID;
    }

    @Override
    public String toString() {
        return "ChannelStatus{ %s is %s }".formatted(name, isBusy ? "busy" : "not busy");
    }

    public String toDiscord() {
        return "%s\t%s".formatted(isBusy ? BUSY_STATUS : FREE_STATUS, name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelID);
    }
}
