package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.interactions.Interaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.function.Predicate;

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
    private static final Color AMBIENT_COLOR = Color.decode("#895FE8");

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
     * Checks whether the given bot has enough permission to execute the given action. For example
     * whether it has enough permissions to ban users.
     * <p>
     * If not, it will handle the situation and respond to the user.
     *
     * @param actionVerb the interaction as verb, for example {@code "ban"} or {@code "kick"}
     * @param permission the required permission to check
     * @param bot the bot attempting to interact with the user
     * @param event the event used to respond to the user
     * @return Whether the bot has the required permission
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    static boolean handleHasBotPermissions(@NotNull String actionVerb,
            @NotNull Permission permission, @NotNull IPermissionHolder bot, @NotNull Guild guild,
            @NotNull Interaction event) {
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

    /**
     * Checks whether the given author has enough permission to execute the given action. For
     * example whether they have enough permissions to ban users.
     * <p>
     * If not, it will handle the situation and respond to the user.
     *
     * @param actionVerb the interaction as verb, for example {@code "ban"} or {@code "kick"}
     * @param permission the required permission to check
     * @param author the author attempting to interact with the target user
     * @param event the event used to respond to the user
     * @return Whether the author has the required permission
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    static boolean handleHasAuthorPermissions(@NotNull String actionVerb,
            @NotNull Permission permission, @NotNull IPermissionHolder author, @NotNull Guild guild,
            @NotNull Interaction event) {
        if (!author.hasPermission(permission)) {
            event
                .reply("You can not %s users in this guild since you do not have the %s permission."
                    .formatted(actionVerb, permission))
                .setEphemeral(true)
                .queue();
            return false;
        }
        return true;
    }

    /**
     * Checks whether the given bot has enough permission to execute the given action. For example
     * whether it has enough permissions to ban users.
     * <p>
     * If not, it will handle the situation and respond to the user.
     *
     * @param actionVerb the interaction as verb, for example {@code "ban"} or {@code "kick"}
     * @param hasRequiredRole a predicate used to identify required roles by their name
     * @param author the author attempting to interact with the target
     * @param event the event used to respond to the user
     * @return Whether the bot has the required permission
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    static boolean handleHasAuthorRole(@NotNull String actionVerb,
            @NotNull Predicate<? super String> hasRequiredRole, @NotNull Member author,
            @NotNull Interaction event) {
        if (author.getRoles().stream().map(Role::getName).anyMatch(hasRequiredRole)) {
            return true;
        }
        event
            .reply("You can not %s users in this guild since you do not have the required role."
                .formatted(actionVerb))
            .setEphemeral(true)
            .queue();
        return false;
    }

    /**
     * Creates a message to be displayed as response to a moderation action.
     *
     * Essentially, it informs others about the action, such as "John banned Bob for playing with
     * the fire.".
     *
     * @param author the author executing the action
     * @param action the action that is executed
     * @param target the target of the action
     * @param extraMessage an optional extra message to be displayed in the response, {@code null}
     *        if not desired
     * @param reason an optional reason for why the action is executed, {@code null} if not desired
     * @return the created response
     */
    static @NotNull MessageEmbed createActionResponse(@NotNull User author, @NotNull Action action,
            @NotNull User target, @Nullable String extraMessage, @Nullable String reason) {
        String description = "%s **%s** (id: %s).".formatted(action.getVerb(), target.getAsTag(),
                target.getId());
        if (extraMessage != null && !extraMessage.isBlank()) {
            description += "\n" + extraMessage;
        }
        if (reason != null && !reason.isBlank()) {
            description += "\n\nReason: " + reason;
        }
        return new EmbedBuilder().setAuthor(author.getAsTag(), null, author.getAvatarUrl())
            .setDescription(description)
            .setTimestamp(Instant.now())
            .setColor(AMBIENT_COLOR)
            .build();
    }

    /**
     * All available moderation actions.
     */
    enum Action {
        /**
         * When a user bans another user.
         */
        BAN("banned"),
        /**
         * When a user unbans another user.
         */
        UNBAN("unbanned"),
        /**
         * When a user kicks another user.
         */
        KICK("kicked");

        private final String verb;

        /**
         * Creates an instance with the given verb
         *
         * @param verb the verb of the action, as it would be used in a sentence, such as "banned"
         *        or "kicked"
         */
        Action(@NotNull String verb) {
            this.verb = verb;
        }

        /**
         * Gets the verb of the action, as it would be used in a sentence.
         * <p>
         * Such as "banned" or "kicked"
         *
         * @return the verb of this action
         */
        @NotNull
        String getVerb() {
            return verb;
        }
    }
}
