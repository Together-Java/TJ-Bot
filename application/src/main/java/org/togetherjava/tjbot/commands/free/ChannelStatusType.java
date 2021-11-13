package org.togetherjava.tjbot.commands.free;

public enum ChannelStatusType {
    FREE("free", ":green_circle:"),
    BUSY("busy", ":red_circle:");

    private final String description;
    private final String emoji;

    ChannelStatusType(String description, String emoji) {
        this.description = description;
        this.emoji = emoji;
    }

    public boolean isFree() {
        return this == FREE;
    }

    public boolean isBusy() {
        return this == BUSY;
    }

    public String description() {
        return description;
    }

    public String toDiscordContentRaw() {
        return emoji;
    }
}
