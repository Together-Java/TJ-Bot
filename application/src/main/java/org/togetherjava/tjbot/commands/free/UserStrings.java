package org.togetherjava.tjbot.commands.free;

import javax.annotation.Nullable;

public enum UserStrings {
    NEW_QUESTION("""
            Thank you for asking a question in an available channel.
            When a helper who can answer this question reads it they will help you.
            Please be patient.

            __Please do not post your question in other channels__
            """),
    MARK_AS_FREE("This channel is now free for a question to be asked."),
    ALREADY_FREE_ERROR("This channel is already free, no changes made"),
    NOT_READY_ERROR("Command not ready please try again in a minute"),
    NOT_MONITORED_ERROR("This channel is not being monitored for free/busy status."
            + " If you believe this channel should be part of the free/busy status system,"
            + " please discuss it with a moderator"),
    NOT_CONFIGURED_ERROR("This guild (%s) is not configured to use the '/free' command,"
            + " please add entries in the config, restart the bot and try again.");

    private final String message;

    UserStrings(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public String formatted(@Nullable Object... args) {
        return message.formatted(args);
    }
}
