package org.togetherjava.tjbot.commands.free;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class containing all the strings sent to users during their interaction with the free command
 * system. This does not include the logged strings or the exception strings.
 */
enum UserStrings {
    NEW_QUESTION("""
            Thank you for asking in an available channel. A helper will be with you shortly!

            __Do not post your question in other channels.__

            Once your issue is resolved, feel free to /free the channel, thanks.
            """),
    MARK_AS_FREE("""
            This channel is now available for a question to be asked.
            """),
    AUTO_MARK_AS_FREE(
            """
                    This channel seems to be inactive and was now marked available for a question to be asked.
                    """),
    ALREADY_FREE_ERROR("""
            This channel is already free, no changes made.
            """),
    NOT_READY_ERROR("""
            Command not ready please try again in a minute.
            """),
    NOT_MONITORED_ERROR("This channel is not being monitored for free/busy status. If you"
            + " believe this channel should be part of the free/busy status system, please"
            + " consult a moderator."),
    NOT_CONFIGURED_ERROR("""
            This guild (%s) is not configured to use the '/free' command.
            Please add entries in the config, restart the bot and try again.
            """);

    private final String message;

    UserStrings(@NotNull String message) {
        this.message = message;
    }

    /**
     * Method to fetch the string that will be sent to a user in reaction to any event triggered by
     * the free command system for that user.
     * 
     * @return the string to send to a user to give them the specified response.
     */
    public @NotNull String message() {
        return message;
    }

    /**
     * Method to fetch the string that will be sent to a user in reaction to any event triggered by
     * the free command system for that user. This can be used to add tagged values in the same way
     * as {@link String#format(String, Object...)}
     *
     * @param args the replacement values for the specified tags.
     * @return the string to send to a user to give them the specified response.
     */
    public @NotNull String formatted(@Nullable Object... args) {
        return message.formatted(args);
    }
}
