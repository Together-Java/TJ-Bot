package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

public final class ModerationUtils {
    /**
     * As stated in {@link Guild#ban(User, int, String)} The reason can be only 512 characters.
     */
    private static final int REASON_MAX_LENGTH = 512;

    ModerationUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Checks if the given reason is above the reason limit.
     */
    public static void reasonLimit(@NotNull String reason, @NotNull SlashCommandEvent event) {
        if (reason.length() > REASON_MAX_LENGTH) {
            event.reply("The reason can not be over " + REASON_MAX_LENGTH + " characters")
                .setEphemeral(true)
                .queue();
        }
    }
}
