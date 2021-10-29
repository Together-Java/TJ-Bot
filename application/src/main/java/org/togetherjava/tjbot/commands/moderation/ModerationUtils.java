package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

public enum ModerationUtils {
    ;

    /**
     * As stated in {@link Guild#ban(User, int, String)} The reason can be only 512 characters.
     */
    private static final int REASON_MAX_LENGTH = 512;

    private ModerationUtils() {
        throw new UnsupportedOperationException(
                "The Moderation Utility class has tin into an error");
    }

    /**
     * The Boolean reasonLimit will check if the handleReason is above the reason limit. If it is it
     * will throw an error and will tell the user that the reason can not be over
     * {@link ModerationUtils#REASON_MAX_LENGTH} If the reason is under the limit it will pass and
     * will allow the command to continue.
     */
    public static boolean handleReason(@NotNull String reason, @NotNull SlashCommandEvent event) {
        if (reason.length() <= REASON_MAX_LENGTH) {
            return true;
        }
        event.reply("The reason can not be over " + REASON_MAX_LENGTH + " characters")
            .setEphemeral(true)
            .queue();
        return true;
    }
}
