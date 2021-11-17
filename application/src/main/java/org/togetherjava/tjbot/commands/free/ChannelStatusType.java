package org.togetherjava.tjbot.commands.free;

import org.jetbrains.annotations.NotNull;

public enum ChannelStatusType {
    FREE("free", ":green_circle:"),
    BUSY("busy", ":red_circle:");

    private final String description;
    private final String emoji;

    ChannelStatusType(@NotNull String description, @NotNull String emoji) {
        this.description = description;
        this.emoji = emoji;
    }

    public boolean isFree() {
        return this == FREE;
    }

    public boolean isBusy() {
        return this == BUSY;
    }

    public @NotNull String description() {
        return description;
    }

    public @NotNull String toDiscordContentRaw() {
        return emoji;
    }
}
