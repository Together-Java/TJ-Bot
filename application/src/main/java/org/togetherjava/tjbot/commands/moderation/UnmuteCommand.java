package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
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
 * This command can unmute muted users. Unmuting can also be paired with a reason. The command will
 * also try to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either unmute other users
 * or to unmute the specific given user (for example a moderator attempting to unmute an admin).
 */
public final class UnmuteCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnmuteCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "unmute";
    private static final String ACTION_VERB = "unmute";
    private final Predicate<String> hasRequiredRole;
    private final Predicate<String> isMuteRole;

    /**
     * Constructs an instance.
     */
    public UnmuteCommand() {
        super(COMMAND_NAME,
                "Unmutes the given already muted user so that they can send messages again",
                SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to unmute", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be unmuted", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getSoftModerationRolePattern())
            .asMatchPredicate();
        isMuteRole = Pattern.compile(Config.getInstance().getMutedRolePattern()).asMatchPredicate();
    }

    private static void handleNotMutedTarget(@NotNull Interaction event) {
        event.reply("The user is not muted.").setEphemeral(true).queue();
    }

    private void unmuteUser(@NotNull Member target, @NotNull Member author, @NotNull String reason,
            @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        User targetUser = target.getUser();
        String dmMessage = """
                Hey there, you have been unmuted in the server %s.
                This means you can now send messages in the server again.
                The reason for the unmute is: %s
                """.formatted(guild.getName(), reason);

        event.getJDA()
            .openPrivateChannelById(targetUser.getId())
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .mapToResult()
            .flatMap(sendDmResult -> {
                logger.info("'{}' ({}) unmuted the user '{}' ({}) in guild '{}' for reason '{}'.",
                        author.getUser().getAsTag(), author.getId(), targetUser.getAsTag(),
                        targetUser.getId(), guild.getName(), reason);

                return guild.removeRoleFromMember(target, getMutedRole(guild).orElseThrow())
                    .reason(reason)
                    .map(unmuteResult -> sendDmResult.isSuccess());
            })
            .map(hasSentDm -> {
                String dmNotice =
                        Boolean.TRUE.equals(hasSentDm) ? "" : "(Unable to send them a DM.)";
                return ModerationUtils.createActionResponse(author.getUser(),
                        ModerationUtils.Action.UNMUTE, targetUser, dmNotice, reason);
            })
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
            .noneMatch(isMuteRole)) {
            handleNotMutedTarget(event);
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
        unmuteUser(Objects.requireNonNull(target), author, reason, guild, event);
    }

    private @NotNull Optional<Role> getMutedRole(@NotNull Guild guild) {
        return guild.getRoles().stream().filter(role -> isMuteRole.test(role.getName())).findAny();
    }
}
