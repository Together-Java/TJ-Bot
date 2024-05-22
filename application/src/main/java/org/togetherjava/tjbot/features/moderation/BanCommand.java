package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.logging.LogMarkers;

import javax.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private static final String ACTION_TITLE = "Ban";
    @SuppressWarnings("StaticCollection")
    private static final List<String> DURATIONS = List.of(ModerationUtils.PERMANENT_DURATION,
            "1 hour", "3 hours", "1 day", "2 days", "3 days", "7 days", "30 days");
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public BanCommand(ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Bans the given user from the server", CommandVisibility.GUILD);

        OptionData durationData = new OptionData(OptionType.STRING, DURATION_OPTION,
                "the duration of the ban, permanent or temporary", true);
        DURATIONS.forEach(duration -> durationData.addChoice(duration, duration));

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to ban", true)
            .addOptions(durationData)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be banned", true)
            .addOptions(new OptionData(OptionType.INTEGER, DELETE_HISTORY_OPTION,
                    "the message history to delete", true)
                .addChoice("none", 0)
                .addChoice("day", 1)
                .addChoice("week", 7));

        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static RestAction<Message> handleAlreadyBanned(Guild.Ban ban, InteractionHook hook) {
        String reason = ban.getReason();
        String reasonText =
                reason == null || reason.isBlank() ? "" : " (reason: %s)".formatted(reason);

        String message =
                "The user '%s' is already banned%s.".formatted(ban.getUser().getName(), reasonText);
        return hook.sendMessage(message).setEphemeral(true);
    }

    private static RestAction<Boolean> sendDm(User target,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason, Guild guild) {
        String durationMessage = temporaryData == null ? "Permanently" : temporaryData.duration();
        String description =
                """
                        Hey there, sorry to tell you but unfortunately you have been banned from the server.
                        If you think this was a mistake, please contact a moderator or admin of this server.""";

        return ModerationUtils.sendModActionDm(ModerationUtils.getModActionEmbed(guild,
                ACTION_TITLE, description, reason, durationMessage, false), target);
    }

    private static MessageEmbed sendFeedback(boolean hasSentDm, User target, Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason) {
        String durationText = "The ban duration is: "
                + (temporaryData == null ? "permanent" : temporaryData.duration());
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "\n(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.BAN, target,
                durationText + dmNoticeText, reason);
    }

    private static Optional<RestAction<Message>> handleNotAlreadyBannedResponse(
            Throwable alreadyBannedFailure, InteractionHook hook, Guild guild, User target) {
        if (alreadyBannedFailure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                return Optional.empty();
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                logger.error("The bot does not have the '{}' permission on the guild '{}'.",
                        Permission.BAN_MEMBERS, guild.getName());
                return Optional.of(hook.sendMessage(
                        "I can not ban users in this guild since I do not have the %s permission."
                            .formatted(Permission.BAN_MEMBERS))
                    .setEphemeral(true));
            }
        }
        logger.warn(LogMarkers.SENSITIVE,
                "Something unexpected went wrong while trying to ban the user '{}'.",
                target.getName(), alreadyBannedFailure);
        return Optional.of(hook.sendMessage("Failed to ban the user due to an unexpected problem.")
            .setEphemeral(true));
    }

    private RestAction<Message> banUserFlow(User target, Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason,
            int deleteHistoryDays, Guild guild, SlashCommandInteractionEvent event) {
        return sendDm(target, temporaryData, reason, guild)
            .flatMap(hasSentDm -> banUser(target, author, temporaryData, reason, deleteHistoryDays,
                    guild)
                .map(banResult -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, temporaryData, reason))
            .flatMap(event.getHook()::sendMessageEmbeds);
    }

    private AuditableRestAction<Void> banUser(User target, Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason,
            int deleteHistoryDays, Guild guild) {
        String durationMessage =
                temporaryData == null ? "permanently" : "for " + temporaryData.duration();
        logger.info(LogMarkers.SENSITIVE,
                "'{}' ({}) banned the user '{}' ({}) {} from guild '{}' and deleted their message history of the last {} days, for reason '{}'.",
                author.getUser().getName(), author.getId(), target.getName(), target.getId(),
                durationMessage, guild.getName(), deleteHistoryDays, reason);

        Instant expiresAt = temporaryData == null ? null : temporaryData.expiresAt();
        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.BAN, expiresAt, reason);

        return guild.ban(target, deleteHistoryDays, TimeUnit.DAYS).reason(reason);
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    private boolean handleChecks(Member bot, Member author, @Nullable Member target,
            CharSequence reason, Guild guild, IReplyCallback event) {
        // Member doesn't exist if attempting to ban a user who is not part of the guild.
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasBotPermissions(ACTION_VERB, Permission.BAN_MEMBERS, bot,
                guild, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasAuthorPermissions(ACTION_VERB, Permission.BAN_MEMBERS, author,
                event)) {
            return false;
        }
        return ModerationUtils.handleReason(reason, event);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
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

        event.deferReply().queue();
        InteractionHook hook = event.getHook();
        // Ban the user, but only if not already banned
        guild.retrieveBan(target).mapToResult().flatMap(alreadyBanned -> {
            if (alreadyBanned.isSuccess()) {
                return handleAlreadyBanned(alreadyBanned.get(), hook);
            }

            return handleNotAlreadyBannedResponse(
                    Objects.requireNonNull(alreadyBanned.getFailure()), hook, guild, target)
                .orElseGet(() -> banUserFlow(target, author, temporaryData.orElse(null), reason,
                        deleteHistoryDays, guild, event));
        }).queue();
    }
}
