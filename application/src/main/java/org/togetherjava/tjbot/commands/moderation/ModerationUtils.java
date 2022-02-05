package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility class offering helpers revolving around user moderation, such as banning or kicking.
 */
public enum ModerationUtils {
    ;

    private static final Logger logger = LoggerFactory.getLogger(ModerationUtils.class);
    /**
     * The maximal character limit for the reason of an auditable action, see for example
     * {@link Guild#ban(User, int, String)}.
     */
    private static final int REASON_MAX_LENGTH = 512;
    /**
     * Human-readable text representing the duration of a permanent action, will be shown to the
     * user as option for selection.
     */
    static final String PERMANENT_DURATION = "permanent";
    /**
     * The ambient color used by moderation actions, often used to streamline the color theme of
     * embeds.
     */
    static final Color AMBIENT_COLOR = Color.decode("#895FE8");
    /**
     * Matches the name of the role that is used to mute users, as used by {@link MuteCommand} and
     * similar.
     */
    public static final Predicate<String> isMuteRole =
            Pattern.compile(Config.getInstance().getMutedRolePattern()).asMatchPredicate();

    /**
     * Checks whether the given reason is valid. If not, it will handle the situation and respond to
     * the user.
     *
     * @param reason the reason to check
     * @param event the event used to respond to the user
     * @return whether the reason is valid
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    static boolean handleReason(@NotNull CharSequence reason, @NotNull IReplyCallback event) {
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
            @NotNull Member author, @NotNull Member target, @NotNull IReplyCallback event) {
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
     * Checks whether the given author and bot can interact with the given role. For example whether
     * they have enough permissions to add or remove this role to users.
     * <p>
     * If not, it will handle the situation and respond to the user.
     *
     * @param bot the bot attempting to interact with the user
     * @param author the author triggering the command
     * @param role the role to interact with
     * @param event the event used to respond to the user
     * @return Whether the author and bot can interact with the role
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    static boolean handleCanInteractWithRole(@NotNull Member bot, @NotNull Member author,
            @NotNull Role role, @NotNull IReplyCallback event) {
        if (!author.canInteract(role)) {
            event
                .reply("The role %s is too powerful for you to interact with."
                    .formatted(role.getAsMention()))
                .setEphemeral(true)
                .queue();
            return false;
        }

        if (!bot.canInteract(role)) {
            event
                .reply("The role %s is too powerful for me to interact with."
                    .formatted(role.getAsMention()))
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
            @NotNull IReplyCallback event) {
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

    private static void handleAbsentTarget(@NotNull String actionVerb,
            @NotNull IReplyCallback event) {
        event
            .reply("I can not %s the given user since they are not part of the guild anymore."
                .formatted(actionVerb))
            .setEphemeral(true)
            .queue();
    }

    /**
     * Checks whether the given bot and author have enough permission to change the roles of a given
     * target. For example whether they have enough permissions to add a role to a user.
     * <p>
     * If not, it will handle the situation and respond to the user.
     * <p>
     * The checks include:
     * <ul>
     * <li>the role does not exist on the guild</li>
     * <li>the target is not member of the guild</li>
     * <li>the bot or author do not have enough permissions to interact with the target</li>
     * <li>the bot or author do not have enough permissions to interact with the role</li>
     * <li>the author does not have the required role for this interaction</li>
     * <li>the bot does not have the MANAGE_ROLES permission</li>
     * <li>the given reason is too long</li>
     * </ul>
     *
     * @param role the role to change, or {@code null} if it does not exist on the guild
     * @param actionVerb the interaction as verb, for example {@code "mute"} or {@code "unmute"}
     * @param target the target user to change roles from, or {@code null} if the user is not member
     *        of the guild
     * @param bot the bot executing this interaction
     * @param author the author attempting to interact with the target
     * @param guild the guild this interaction is executed on
     * @param hasRequiredRole a predicate used to identify required roles by their name
     * @param reason the reason for this interaction
     * @param event the event used to respond to the user
     * @return Whether the bot and the author have enough permission
     */
    @SuppressWarnings({"MethodWithTooManyParameters", "BooleanMethodNameMustStartWithQuestion",
            "squid:S107"})
    static boolean handleRoleChangeChecks(@Nullable Role role, @NotNull String actionVerb,
            @Nullable Member target, @NotNull Member bot, @NotNull Member author,
            @NotNull Guild guild, @NotNull Predicate<? super String> hasRequiredRole,
            @NotNull CharSequence reason, @NotNull IReplyCallback event) {
        if (role == null) {
            event
                .reply("Can not %s the user, unable to find the corresponding role on this server"
                    .formatted(actionVerb))
                .setEphemeral(true)
                .queue();
            logger.warn("The guild '{}' does not have a role to {} users.", guild.getName(),
                    actionVerb);
            return false;
        }

        // Member doesn't exist if attempting to change roles of a user who is not part of the guild
        // anymore.
        if (target == null) {
            handleAbsentTarget(actionVerb, event);
            return false;
        }
        if (!handleCanInteractWithTarget(actionVerb, bot, author, target, event)) {
            return false;
        }
        if (!handleCanInteractWithRole(bot, author, role, event)) {
            return false;
        }
        if (!handleHasAuthorRole(actionVerb, hasRequiredRole, author, event)) {
            return false;
        }
        if (!handleHasBotPermissions(actionVerb, Permission.MANAGE_ROLES, bot, guild, event)) {
            return false;
        }
        return ModerationUtils.handleReason(reason, event);
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
            @NotNull IReplyCallback event) {
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
            @NotNull IReplyCallback event) {
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
     * <p>
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
    static @NotNull MessageEmbed createActionResponse(@NotNull User author,
            @NotNull ModerationAction action, @NotNull User target, @Nullable String extraMessage,
            @Nullable String reason) {
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
     * Gets the role used to mute a member in a guild.
     *
     * @param guild the guild to get the muted role from
     * @return the muted role, if found
     */
    public static @NotNull Optional<Role> getMutedRole(@NotNull Guild guild) {
        return guild.getRoles().stream().filter(role -> isMuteRole.test(role.getName())).findAny();
    }

    /**
     * Computes a temporary data wrapper representing the action with the given duration.
     *
     * @param durationText the duration of the action, either {@code "permanent"} or a time window
     *        such as {@code 1 day} or {@code 2 minutes}. Supports all units supported by
     *        {@link Instant#plus(long, TemporalUnit)}.
     * @return the temporary data represented by the given duration or empty if the duration is
     *         {@code "permanent"}
     */
    static @NotNull Optional<TemporaryData> computeTemporaryData(@NotNull String durationText) {
        if (PERMANENT_DURATION.equals(durationText)) {
            return Optional.empty();
        }

        // 1 minute, 1 day, 2 days, ...
        String[] data = durationText.split(" ", 2);
        int duration = Integer.parseInt(data[0]);
        ChronoUnit unit = switch (data[1]) {
            case "minute", "minutes" -> ChronoUnit.MINUTES;
            case "hour", "hours" -> ChronoUnit.HOURS;
            case "day", "days" -> ChronoUnit.DAYS;
            default -> throw new IllegalArgumentException(
                    "Unsupported mute duration: " + durationText);
        };

        return Optional.of(new TemporaryData(Instant.now().plus(duration, unit), durationText));
    }

    /**
     * Wrapper to hold data relevant to temporary actions, for example the time it expires.
     *
     * @param expiresAt the time the temporary action expires
     * @param duration a human-readable text representing the duration of the temporary action, such
     *        as {@code "1 day"}.
     */
    record TemporaryData(@NotNull Instant expiresAt, @NotNull String duration) {
    }
}
