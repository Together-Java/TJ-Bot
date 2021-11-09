package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.interactions.Interaction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.config.FreeCommandConfig;

import java.time.OffsetDateTime;

public class FreeUtil {
    // private constructor to prevent this class getting instantiated
    private FreeUtil() {}

    /**
     * Helper method to easily send ephemeral messages to users.
     * 
     * @param interaction The event or hook that this message is responding to
     * @param message The text to be display for the user to read.
     */
    public static void sendErrorMessage(@NotNull Interaction interaction, @NotNull String message) {
        interaction.reply(message).setEphemeral(true).queue();
    }

    public static @NotNull OffsetDateTime anHourAgo() {
        return OffsetDateTime.now().minus(FreeCommandConfig.INACTIVE_DURATION, FreeCommandConfig.INACTIVE_UNIT);
    }

}
