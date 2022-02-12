package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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
 * This command can mute users. Muting can also be paired with a reason. The command will also try
 * to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either mute other users or
 * to mute the specific given user (for example a moderator attempting to mute an admin).
 */
public final class MuteCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MuteCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String DURATION_OPTION = "duration";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "mute";
    private static final String ACTION_VERB = "mute";
    @SuppressWarnings("StaticCollection")
    private static final List<String> DURATIONS = List.of("10 minutes", "30 minutes", "1 hour",
            "3 hours", "1 day", "3 days", "7 days", ModerationUtils.PERMANENT_DURATION);
    private final Predicate<String> hasRequiredRole;
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public MuteCommand(@NotNull ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Mutes the given user so that they can not send messages anymore",
                CommandVisibility.GUILD);

        OptionData durationData = new OptionData(OptionType.STRING, DURATION_OPTION,
                "the duration of the mute, permanent or temporary", true);
        DURATIONS.forEach(duration -> durationData.addChoice(duration, duration));

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to mute", true)
            .addOptions(durationData)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be muted", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getSoftModerationRolePattern())
            .asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static void handleAlreadyMutedTarget(@NotNull IReplyCallback event) {
        event.reply("The user is already muted.").setEphemeral(true).queue();
    }

    private static RestAction<Boolean> sendDm(@NotNull ISnowflake target,
            @Nullable ModerationUtils.TemporaryData temporaryData, @NotNull String reason,
            @NotNull Guild guild, @NotNull JDA jda) {
        String durationMessage =
                temporaryData == null ? "permanently" : "for " + temporaryData.duration();
        String dmMessage =
                """
                        Hey there, sorry to tell you but unfortunately you have been muted %s in the server %s.
                        This means you can no longer send any messages in the server until you have been unmuted again.
                        If you think this was a mistake, please contact a moderator or admin of the server.
                        The reason for the mute is: %s
                        """
                    .formatted(durationMessage, guild.getName(), reason);
        return jda.openPrivateChannelById(target.getId())
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull Member target,
            @NotNull Member author, @Nullable ModerationUtils.TemporaryData temporaryData,
            @NotNull String reason) {
        String durationText = "The mute duration is: "
                + (temporaryData == null ? "permanent" : temporaryData.duration());
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "\n(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.MUTE,
                target.getUser(), durationText + dmNoticeText, reason);
    }

    private AuditableRestAction<Void> muteUser(@NotNull Member target, @NotNull Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, @NotNull String reason,
            @NotNull Guild guild) {
        String durationMessage =
                temporaryData == null ? "permanently" : "for " + temporaryData.duration();
        logger.info("'{}' ({}) muted the user '{}' ({}) {} in guild '{}' for reason '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getUser().getAsTag(),
                target.getId(), durationMessage, guild.getName(), reason);

        Instant expiresAt = temporaryData == null ? null : temporaryData.expiresAt();
        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.MUTE, expiresAt, reason);

        return guild.addRoleToMember(target, ModerationUtils.getMutedRole(guild).orElseThrow())
            .reason(reason);
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    private void muteUserFlow(@NotNull Member target, @NotNull Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, @NotNull String reason,
            @NotNull Guild guild, @NotNull SlashCommandInteractionEvent event) {
        sendDm(target, temporaryData, reason, guild, event.getJDA())
            .flatMap(hasSentDm -> muteUser(target, author, temporaryData, reason, guild)
                .map(banResult -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, temporaryData, reason))
            .flatMap(event::replyEmbeds)
            .queue();
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull IReplyCallback event) {
        if (!ModerationUtils.handleRoleChangeChecks(
                ModerationUtils.getMutedRole(guild).orElse(null), ACTION_VERB, target, bot, author,
                guild, hasRequiredRole, reason, event)) {
            return false;
        }
        if (Objects.requireNonNull(target)
            .getRoles()
            .stream()
            .map(Role::getName)
            .anyMatch(ModerationUtils.isMuteRole)) {
            handleAlreadyMutedTarget(event);
            return false;
        }
        return true;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        Member target = Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null")
            .getAsMember();
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

        if (!handleChecks(bot, author, target, reason, guild, event)) {
            return;
        }

        muteUserFlow(Objects.requireNonNull(target), author, temporaryData.orElse(null), reason,
                guild, event);
    }
}