package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.Interaction;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class offering helpers revolving around user moderation, such as banning or kicking.
 */
public enum ModerationUtils {
    ;

    /**
     * The maximal character limit for the reason of an auditable action, see for example
     * {@link Guild#ban(User, int, String)}.
     */
    private static final int REASON_MAX_LENGTH = 512;

    /**
     * Checks whether the given reason is valid. If not, it will handle the situation and respond to
     * the user.
     *
     * @param reason the reason to check
     * @param event the event used to respond to the user
     * 
     * @return whether the reason is valid
     */
    public static boolean handleReason(@NotNull CharSequence reason, @NotNull Interaction event) {
        if (reason.length() <= REASON_MAX_LENGTH) {
            return true;
        }

        event
            .reply("The reason can not be longer than %d characters (current length is %d)"
                .formatted(REASON_MAX_LENGTH, reason.length()))
            .setEphemeral(true)
            .queue();
        return false;
    }
}
