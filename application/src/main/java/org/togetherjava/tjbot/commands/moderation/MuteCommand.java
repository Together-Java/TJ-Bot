package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.Result;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

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
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "mute";
    private static final String ACTION_VERB = "mute";
    private final Predicate<String> hasRequiredRole;
    private final Predicate<String> isMuteRole;

    /**
     * Constructs an instance.
     */
    public MuteCommand() {
        super(COMMAND_NAME, "Mutes the given user so that they can not send messages anymore",
                SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to mute", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be muted", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getSoftModerationRolePattern())
            .asMatchPredicate();
        isMuteRole = Pattern.compile(Config.getInstance().getMutedRolePattern()).asMatchPredicate();
    }

    private static void handleAlreadyMutedTarget(@NotNull Interaction event) {
        event.reply("The user is already muted.").setEphemeral(true).queue();
    }

    private static RestAction<Boolean> sendDm(@NotNull ISnowflake target, @NotNull String reason,
            @NotNull Guild guild, @NotNull GenericEvent event) {
        String dmMessage =
                """
                        Hey there, sorry to tell you but unfortunately you have been muted in the server %s.
                        This means you can no longer send any messages in the server until you have been unmuted again.
                        If you think this was a mistake, please contact a moderator or admin of the server.
                        The reason for the mute is: %s
                        """
                    .formatted(guild.getName(), reason);
        return event.getJDA()
            .openPrivateChannelById(target.getId())
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull Member target,
            @NotNull Member author, @NotNull String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationUtils.Action.MUTE,
                target.getUser(), dmNoticeText, reason);
    }

    private AuditableRestAction<Void> muteUser(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild) {
        logger.info("'{}' ({}) muted the user '{}' ({}) in guild '{}' for reason '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getUser().getAsTag(),
                target.getId(), guild.getName(), reason);
        return guild.addRoleToMember(target, getMutedRole(guild).orElseThrow()).reason(reason);
    }

    private void muteUserFlow(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        sendDm(target, reason, guild, event)
            .flatMap(hasSentDm -> muteUser(target, author, reason, guild)
                .map(banResult -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event::replyEmbeds)
            .queue();
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull Interaction event) {
        if (!ModerationUtils.handleRoleChangeChecks(getMutedRole(guild).orElse(null), ACTION_VERB,
                target, bot, author, guild, hasRequiredRole, reason, event)) {
            return false;
        }
        if (Objects.requireNonNull(target)
            .getRoles()
            .stream()
            .map(Role::getName)
            .anyMatch(isMuteRole)) {
            handleAlreadyMutedTarget(event);
            return false;
        }
        return true;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member target = Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null")
            .getAsMember();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, target, reason, guild, event)) {
            return;
        }

        muteUserFlow(Objects.requireNonNull(target), author, reason, guild, event);
    }

    private @NotNull Optional<Role> getMutedRole(@NotNull Guild guild) {
        return guild.getRoles().stream().filter(role -> isMuteRole.test(role.getName())).findAny();
    }
}
