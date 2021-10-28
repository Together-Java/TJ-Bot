package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

public final class ModerationUtils {
    /**
     * As stated in {@link Guild#ban(User, int, String)} The reason can be only 512 characters.
     */
    private final int reasonMaxLength;

    public ModerationUtils() {
        reasonMaxLength = 512;
    }

    /**
     * Checks if the given reason is above the reason limit.
     */
    public static void reasonLimit(@NotNull String reason, @NotNull SlashCommandEvent event) {
        ModerationUtils reasonLimit = new ModerationUtils();
        if (reason.length() > reasonLimit.reasonMaxLength) {
            event.reply("The reason can not be over " + reasonLimit.reasonMaxLength + " characters")
                .setEphemeral(true)
                .queue();
        }
    }
}
