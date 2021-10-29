package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

public enum ModerationUtils {
    ;

    /**
     * As stated in {@link Guild#ban(User, int, String)} <br>
     * The reason can be only 512 characters.
     */
    private static final int REASON_MAX_LENGTH = 512;

    /**
     * @param reason The Boolean reasonLimit will check if the reason is above the provided limit.
     *        <br>
     *        <br>
     *        If it is it will throw an error and will tell the user that the reason can not be over
     *        the {@link ModerationUtils#REASON_MAX_LENGTH} <br>
     *        If the reason is under the limit it will pass and will allow the command to continue.
     *
     * @return {@link ModerationUtils#REASON_MAX_LENGTH}
     */
    public static boolean handleReason(@NotNull String reason, @NotNull SlashCommandEvent event) {
        if (reason.length() <= REASON_MAX_LENGTH) {
            return true;
        }
        event.reply("The reason can not be over " + REASON_MAX_LENGTH + " characters")
            .setEphemeral(true)
            .queue();
        return false;
    }
}
