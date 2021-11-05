package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.Interaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class offering helpers revolving around user moderation, such as banning or kicking.
 */
enum ModerationUtils {
    ;

    private static final Logger logger = LoggerFactory.getLogger(ModerationUtils.class);
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
     * @return whether the reason is valid
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    static boolean handleReason(@NotNull CharSequence reason, @NotNull Interaction event) {
        if (reason.length() <= REASON_MAX_LENGTH) {
            return true;
        }

        event
            .reply("The reason can not be longer than %d characters (current length is %d)."
                .formatted(REASON_MAX_LENGTH, reason.length()))
            .setEphemeral(true)
            .queue();
        return false;
    }

    /**
     * Checks whether the given author and bot can interact with the target user. For example
     * whether they have enough permissions to ban the user.
     * <p>
     * If not, it will handle the situation and respond to the user.
     *
     * @param actionVerb the interaction as verb, for example {@code "ban"} or {@code "kick"}
     * @param bot the bot attempting to interact with the user
     * @param author the author triggering the command
     * @param target the target user of the interaction
     * @param event the event used to respond to the user
     * @return Whether the author and bot can interact with the target user
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    static boolean handleCanInteractWithTarget(@NotNull String actionVerb, @NotNull Member bot,
            @NotNull Member author, @NotNull Member target, @NotNull Interaction event) {
        String targetTag = target.getUser().getAsTag();
        if (!author.canInteract(target)) {
            event
                .reply("The user %s is too powerful for you to %s.".formatted(targetTag,
                        actionVerb))
                .setEphemeral(true)
                .queue();
            return false;
        }

        if (!bot.canInteract(target)) {
            event
                .reply("The user %s is too powerful for me to %s.".formatted(targetTag, actionVerb))
                .setEphemeral(true)
                .queue();
            return false;
        }
        return true;
    }

    /**
     * Checks whether the given author and bot have enough permission to execute the given action.
     * For example whether they have enough permissions to ban users.
     * <p>
     * If not, it will handle the situation and respond to the user.
     *
     * @param actionVerb the interaction as verb, for example {@code "ban"} or {@code "kick"}
     * @param permission the required permission to check
     * @param bot the bot attempting to interact with the user
     * @param author the author triggering the command
     * @param event the event used to respond to the user
     * @return Whether the author and bot have the required permission
     */
    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    static boolean handleHasPermissions(@NotNull String actionVerb, @NotNull Permission permission,
            @NotNull IPermissionHolder bot, @NotNull IPermissionHolder author, @NotNull Guild guild,
            @NotNull Interaction event) {
        if (!author.hasPermission(permission)) {
            event
                .reply("You can not %s users in this guild since you do not have the %s permission."
                    .formatted(actionVerb, permission))
                .setEphemeral(true)
                .queue();
            return false;
        }

        if (!bot.hasPermission(permission)) {
            event
                .reply("I can not %s users in this guild since I do not have the %s permission."
                    .formatted(actionVerb, permission))
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have the '{}' permission on the guild '{}'.", permission,
                    guild.getName());
            return false;
        }
        return true;
    }
}
