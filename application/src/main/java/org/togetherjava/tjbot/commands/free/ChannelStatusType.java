package org.togetherjava.tjbot.commands.free;

import org.jetbrains.annotations.NotNull;

enum ChannelStatusType {
    FREE("free", ":white_check_mark:"),
    BUSY("busy", ":x:");

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
