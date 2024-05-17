package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.logging.LogMarkers;

import javax.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private static final String ACTION_TITLE = "Mute";
    @SuppressWarnings("StaticCollection")
    private static final List<String> DURATIONS = List.of("10 minutes", "30 minutes", "1 hour",
            "3 hours", "1 day", "3 days", "7 days", ModerationUtils.PERMANENT_DURATION);
    private final ModerationActionsStore actionsStore;
    private final Config config;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     * @param config the config to use for this
     */
    public MuteCommand(ModerationActionsStore actionsStore, Config config) {
        super(COMMAND_NAME, "Mutes the given user so that they can not send messages anymore",
                CommandVisibility.GUILD);

        OptionData durationData = new OptionData(OptionType.STRING, DURATION_OPTION,
                "the duration of the mute, permanent or temporary", true);
        DURATIONS.forEach(duration -> durationData.addChoice(duration, duration));

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to mute", true)
            .addOptions(durationData)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be muted", true);

        this.config = config;
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static void handleAlreadyMutedTarget(IReplyCallback event) {
        event.reply("The user is already muted.").setEphemeral(true).queue();
    }

    private static RestAction<Boolean> sendDm(User target,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason, Guild guild) {
        String durationMessage = temporaryData == null ? "Permanent" : temporaryData.duration();
        String description =
                """
                        Hey there, sorry to tell you but unfortunately you have been muted.
                        This means you can no longer send any messages in the server until you have been unmuted again.""";

        return ModerationUtils.sendModActionDm(ModerationUtils.getModActionEmbed(guild,
                ACTION_TITLE, description, reason, durationMessage, true), target);
    }

    private static MessageEmbed sendFeedback(boolean hasSentDm, Member target, Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason) {
        String durationText = "The mute duration is: "
                + (temporaryData == null ? "permanent" : temporaryData.duration());
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "\n(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.MUTE,
                target.getUser(), durationText + dmNoticeText, reason);
    }

    private AuditableRestAction<Void> muteUser(Member target, Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason, Guild guild) {
        String durationMessage =
                temporaryData == null ? "permanently" : "for " + temporaryData.duration();
        logger.info(LogMarkers.SENSITIVE,
                "'{}' ({}) muted the user '{}' ({}) {} in guild '{}' for reason '{}'.",
                author.getUser().getName(), author.getId(), target.getUser().getName(),
                target.getId(), durationMessage, guild.getName(), reason);

        Instant expiresAt = temporaryData == null ? null : temporaryData.expiresAt();
        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.MUTE, expiresAt, reason);

        return guild
            .addRoleToMember(target, ModerationUtils.getMutedRole(guild, config).orElseThrow())
            .reason(reason);
    }

    private void muteUserFlow(Member target, Member author,
            @Nullable ModerationUtils.TemporaryData temporaryData, String reason, Guild guild,
            SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        sendDm(target.getUser(), temporaryData, reason, guild)
            .flatMap(hasSentDm -> muteUser(target, author, temporaryData, reason, guild)
                .map(result -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, temporaryData, reason))
            .flatMap(event.getHook()::sendMessageEmbeds)
            .queue();
    }

    private boolean handleChecks(Member bot, Member author, @Nullable Member target,
            CharSequence reason, Guild guild, IReplyCallback event) {
        if (!ModerationUtils.handleRoleChangeChecks(
                ModerationUtils.getMutedRole(guild, config).orElse(null), ACTION_VERB, target, bot,
                author, guild, reason, event)) {
            return false;
        }
        if (Objects.requireNonNull(target)
            .getRoles()
            .stream()
            .map(Role::getName)
            .anyMatch(ModerationUtils.getIsMutedRolePredicate(config))) {
            handleAlreadyMutedTarget(event);
            return false;
        }
        return true;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
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
