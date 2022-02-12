package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.Result;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * This command can ban users and optionally remove their messages from the past days. Banning can
 * also be paired with a ban reason. The command will also try to DM the user to inform them about
 * the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either ban other users or
 * to ban the specific given user (for example a moderator attempting to ban an admin).
 */
public final class BanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String DURATION_OPTION = "duration";
    private static final String DELETE_HISTORY_OPTION = "delete-history";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "ban";
    private static final String ACTION_VERB = "ban";
    @SuppressWarnings("StaticCollection")
    private static final List<String> DURATIONS = List.of(ModerationUtils.PERMANENT_DURATION,
            "1 hour", "3 hours", "1 day", "2 days", "3 days", "7 days", "30 days");
    private final Predicate<String> hasRequiredRole;
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public BanCommand(@NotNull ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Bans the given user from the server", CommandVisibility.GUILD);

        OptionData durationData = new OptionData(OptionType.STRING, DURATION_OPTION,
                "the duration of the ban, permanent or temporary", true);
        DURATIONS.forEach(duration -> durationData.addChoice(duration, duration));

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to ban", true)
            .addOptions(durationData)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be banned", true)
            .addOptions(new OptionData(OptionType.INTEGER, DELETE_HISTORY_OPTION,
                    "the amount of days of the message history to delete, none means no messages are deleted.",
                    true).addChoice("none", 0).addChoice("recent", 1).addChoice("all", 7));

        hasRequiredRole = Pattern.compile(Config.getInstance().getHeavyModerationRolePattern())
            .asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static RestAction<InteractionHook> handleAlreadyBanned(@NotNull Guild.Ban ban,
            @NotNull IReplyCallback event) {
        String reason = ban.getReason();
        String reasonText =
                reason == null || reason.isBlank() ? "" : " (reason: %s)".formatted(reason);

        String message = "The user '%s' is already banned%s.".formatted(ban.getUser().getAsTag(),
                reasonText);
        return event.reply(message).setEphemeral(true);
    }

    private static RestAction<Boolean> sendDm(@NotNull ISnowflake target,
            @Nullable ModerationUtils.TemporaryData temporaryData, @NotNull String reason,
            @NotNull Guild guild, @NotNull JDA jda) {
        String durationMessage =
                temporaryData == null ? "permanently" : "for " + temporaryData.duration();
        String dmMessage =
                """
                        Hey there, sorry to tell you but unfortunately you have been banned %s from the server %s.
                        If you think this was a mistake, please contact a moderator or admin of the server.
                        The reason for the ban is: %s
                        """
                    .formatted(durationMessage, guild.getName(), reason);

        return jda.openPrivateChannelById(target.getId())
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull User target,
            @NotNull Member author, @Nullable ModerationUtils.TemporaryData temporaryData,
            @NotNull String reason) {
        String durationText = "The ban duration is: "
                + (temporaryData == null ? "permanent" : temporaryData.duration());
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "\n(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.BAN, target,
                durationText + dmNoticeText, reason);
    }

    private static Optional<RestAction<InteractionHook>> handleNotAlreadyBannedResponse(
            @NotNull Throwable alreadyBannedFailure, @NotNull IReplyCallback event,
            @NotNull Guild guild, @NotNull User target) {
        if (alreadyBannedFailure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                return Optional.empty();
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                logger.error("The bot does not have the '{}' permission on the guild '{}'.",
                        Permission.BAN_MEMBERS, guild.getName());
                return Optional.of(event.reply(
                        "I can not ban users in this guild since I do not have the %s permission."
                            .formatted(Permission.BAN_MEMBERS))
                    .setEphemeral(true));
            }
        }
        logger.warn("Something unexpected went wrong while trying to ban the user '{}'.",
                target.getAsTag(), alreadyBannedFailure);
        return Optional.of(event.reply("Failed to ban the user due to an unexpected problem.")
            .setEphemeral(true));
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    private RestAction<InteractionHook> banUserFlow(@NotNull User target, @NotNull Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, @NotNull String reason,
            int deleteHistoryDays, @NotNull Guild guild,
            @NotNull SlashCommandInteractionEvent event) {
        return sendDm(target, temporaryData, reason, guild, event.getJDA())
            .flatMap(hasSentDm -> banUser(target, author, temporaryData, reason, deleteHistoryDays,
                    guild).map(banResult -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, temporaryData, reason))
            .flatMap(event::replyEmbeds);
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    private AuditableRestAction<Void> banUser(@NotNull User target, @NotNull Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, @NotNull String reason,
            int deleteHistoryDays, @NotNull Guild guild) {
        String durationMessage =
                temporaryData == null ? "permanently" : "for " + temporaryData.duration();
        logger.info(
                "'{}' ({}) banned the user '{}' ({}) {} from guild '{}' and deleted their message history of the last {} days, for reason '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getAsTag(), target.getId(),
                durationMessage, guild.getName(), deleteHistoryDays, reason);

        Instant expiresAt = temporaryData == null ? null : temporaryData.expiresAt();
        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.BAN, expiresAt, reason);

        return guild.ban(target, deleteHistoryDays, reason);
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull IReplyCallback event) {
        // Member doesn't exist if attempting to ban a user who is not part of the guild.
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasAuthorRole(ACTION_VERB, hasRequiredRole, author, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasBotPermissions(ACTION_VERB, Permission.BAN_MEMBERS, bot,
                guild, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasAuthorPermissions(ACTION_VERB, Permission.BAN_MEMBERS, author,
                guild, event)) {
            return false;
        }
        return ModerationUtils.handleReason(reason, event);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null");
        User target = targetOption.getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();
        String duration =
                Objects.requireNonNull(event.getOption(DURATION_OPTION), "The duration is null")
                    .getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();
        Optional<ModerationUtils.TemporaryData> temporaryData =
                ModerationUtils.computeTemporaryData(duration);

        if (!handleChecks(bot, author, targetOption.getAsMember(), reason, guild, event)) {
            return;
        }

        int deleteHistoryDays = Math
            .toIntExact(Objects.requireNonNull(event.getOption(DELETE_HISTORY_OPTION)).getAsLong());

        // Ban the user, but only if not already banned
        guild.retrieveBan(target).mapToResult().flatMap(alreadyBanned -> {
            if (alreadyBanned.isSuccess()) {
                return handleAlreadyBanned(alreadyBanned.get(), event);
            }

            return handleNotAlreadyBannedResponse(
                    Objects.requireNonNull(alreadyBanned.getFailure()), event, guild, target)
                        .orElseGet(() -> banUserFlow(target, author, temporaryData.orElse(null),
                                reason, deleteHistoryDays, guild, event));
        }).queue();
    }
}