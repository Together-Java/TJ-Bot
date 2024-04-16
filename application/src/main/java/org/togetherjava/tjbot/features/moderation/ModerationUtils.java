package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.Result;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.moderation.modmail.ModMailCommand;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility class offering helpers revolving around user moderation, such as banning or kicking.
 */
public class ModerationUtils {
    private ModerationUtils() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    private static final Logger logger = LoggerFactory.getLogger(ModerationUtils.class);
    /**
     * The maximal character limit for the reason of an auditable action, see for example
     * {@link AuditableRestAction#reason(String)}.
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
    public static final Color AMBIENT_COLOR = Color.decode("#895FE8");

    /**
     * Checks whether the given reason is valid. If not, it will handle the situation and respond to
     * the user.
     *
     * @param reason the reason to check
     * @param event the event used to respond to the user
     * @return whether the reason is valid
     */
    static boolean handleReason(CharSequence reason, IReplyCallback event) {
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
    public static boolean handleCanInteractWithTarget(String actionVerb, Member bot, Member author,
            Member target, IReplyCallback event) {
        String targetTag = target.getUser().getName();
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
    static boolean handleCanInteractWithRole(Member bot, Member author, Role role,
            IReplyCallback event) {
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
    static boolean handleHasBotPermissions(String actionVerb, Permission permission,
            IPermissionHolder bot, Guild guild, IReplyCallback event) {
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

    private static void handleAbsentTarget(String actionVerb, IReplyCallback event) {
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
     * @param reason the reason for this interaction
     * @param event the event used to respond to the user
     * @return Whether the bot and the author have enough permission
     */
    // Sonar complains about having too many parameters. Not incorrect, but not easy to work around
    // for now
    @SuppressWarnings({"MethodWithTooManyParameters", "squid:S107"})
    static boolean handleRoleChangeChecks(@Nullable Role role, String actionVerb,
            @Nullable Member target, Member bot, Member author, Guild guild, CharSequence reason,
            IReplyCallback event) {
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
    static boolean handleHasAuthorPermissions(String actionVerb, Permission permission,
            IPermissionHolder author, IReplyCallback event) {
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
    static MessageEmbed createActionResponse(User author, ModerationAction action, User target,
            @Nullable String extraMessage, @Nullable String reason) {
        String description =
                "%s **%s** (id: %s).".formatted(action.getVerb(), target.getName(), target.getId());
        if (extraMessage != null && !extraMessage.isBlank()) {
            description += "\n" + extraMessage;
        }
        if (reason != null && !reason.isBlank()) {
            description += "\n\nReason: " + reason;
        }

        String avatarOrDefaultUrl = author.getEffectiveAvatarUrl();

        return new EmbedBuilder().setAuthor(author.getName(), null, avatarOrDefaultUrl)
            .setDescription(description)
            .setTimestamp(Instant.now())
            .setColor(AMBIENT_COLOR)
            .build();
    }

    /**
     * Gets a predicate that identifies the role used to mute a member in a guild.
     *
     * @param config the config used to identify the muted role
     * @return predicate that matches the name of the muted role
     */
    public static Predicate<String> getIsMutedRolePredicate(Config config) {
        return Pattern.compile(config.getMutedRolePattern()).asMatchPredicate();
    }

    /**
     * Gets the role used to mute a member in a guild.
     *
     * @param guild the guild to get the muted role from
     * @param config the config used to identify the muted role
     * @return the muted role, if found
     */
    public static Optional<Role> getMutedRole(Guild guild, Config config) {
        Predicate<String> isMutedRole = getIsMutedRolePredicate(config);
        return guild.getRoles().stream().filter(role -> isMutedRole.test(role.getName())).findAny();
    }

    /**
     * Gets a predicate that identifies the role used to quarantine a member in a guild.
     *
     * @param config the config used to identify the quarantined role
     * @return predicate that matches the name of the quarantined role
     */
    public static Predicate<String> getIsQuarantinedRolePredicate(Config config) {
        return Pattern.compile(config.getQuarantinedRolePattern()).asMatchPredicate();
    }

    /**
     * Gets the role used to quarantine a member in a guild.
     *
     * @param guild the guild to get the quarantined role from
     * @param config the config used to identify the quarantined role
     * @return the quarantined role, if found
     */
    public static Optional<Role> getQuarantinedRole(Guild guild, Config config) {
        Predicate<String> isQuarantinedRole = getIsQuarantinedRolePredicate(config);
        return guild.getRoles()
            .stream()
            .filter(role -> isQuarantinedRole.test(role.getName()))
            .findAny();
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
    static Optional<TemporaryData> computeTemporaryData(String durationText) {
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
            default -> throw new IllegalArgumentException("Unsupported duration: " + durationText);
        };

        return Optional.of(new TemporaryData(Instant.now().plus(duration, unit), durationText));
    }

    /**
     * Creates a nice looking embed for the mod action taken.
     *
     * @param guild the guild in which the action has been taken
     * @param actionTitle the mod action as title e.g, Ban
     * @param description a short description explaining the action
     * @param reason reason for the action taken
     * @param showModmailAdvice whether to advice on how to use the modmail command
     * @return the embed
     */
    static RestAction<EmbedBuilder> getModActionEmbed(Guild guild, String actionTitle,
            String description, String reason, boolean showModmailAdvice) {
        EmbedBuilder modActionEmbed =
                new EmbedBuilder().setAuthor(guild.getName(), null, guild.getIconUrl())
                    .setTitle(actionTitle)
                    .setDescription(description)
                    .addField("Reason", reason, false)
                    .setColor(ModerationUtils.AMBIENT_COLOR);

        if (!showModmailAdvice) {
            return new CompletedRestAction<>(guild.getJDA(), modActionEmbed);
        }

        return MessageUtils.mentionGlobalSlashCommand(guild.getJDA(), ModMailCommand.COMMAND_NAME)
            .map(commandMention -> modActionEmbed.appendDescription(
                    "%n%nTo get in touch with a moderator, you can use the %s command here in this chat. Your message will then be forwarded and a moderator will get back to you soon 😊"
                        .formatted(commandMention)));
    }

    /**
     * Creates a nice looking embed for the mod action taken with duration
     *
     * @param guild the guild in which the action has been taken
     * @param actionTitle the mod action itself
     * @param description a short description explaining the action
     * @param reason reason for the action taken
     * @param duration the duration of mod action
     * @param showModmailAdvice whether to advice on how to use the modmail command
     * @return the embed
     */
    static RestAction<EmbedBuilder> getModActionEmbed(Guild guild, String actionTitle,
            String description, String reason, String duration, boolean showModmailAdvice) {
        return getModActionEmbed(guild, actionTitle, description, reason, showModmailAdvice)
            .map(embedBuilder -> embedBuilder.addField("Duration", duration, false));
    }

    /**
     * @param embedBuilder rest action to generate embed from
     * @param target the user to send the generated embed
     * @return boolean rest action, weather the dm is sent successfully
     */
    static RestAction<Boolean> sendModActionDm(RestAction<EmbedBuilder> embedBuilder, User target) {
        return embedBuilder.map(EmbedBuilder::build)
            .flatMap(embed -> target.openPrivateChannel()
                .flatMap(channel -> channel.sendMessageEmbeds(embed)))
            .mapToResult()
            .map(Result::isSuccess);
    }

    /**
     * Wrapper to hold data relevant to temporary actions, for example the time it expires.
     *
     * @param expiresAt the time the temporary action expires
     * @param duration a human-readable text representing the duration of the temporary action, such
     *        as {@code "1 day"}.
     */
    record TemporaryData(Instant expiresAt, String duration) {
    }
}
