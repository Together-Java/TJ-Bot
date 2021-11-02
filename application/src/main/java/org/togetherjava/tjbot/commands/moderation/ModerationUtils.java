package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;

/**
 * This contains if statements that are common in all the moderation commands suck se the if
 * statement for the reason length
 */
public enum ModerationUtils {
    ;

    /**
     * As stated in {@link Guild#ban(User, int, String)} <br>
     * The reason can be only 512 characters.
     */
    private static final int REASON_MAX_LENGTH = 512;

    /**
     * This boolean checks if the reason that the user has entered violates the max character length
     * or not. <br>
     * If it does the bot will tell the user has violated the mex character length and will
     * terminate the command <br>
     * If it does bot the bot will be allowed to continue running the command.
     * 
     * @param reason the reason of the action such as banning.
     * @throws IllegalArgumentException if the reason is over 512 characters.
     *
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
