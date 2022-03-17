package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * This command can quarantine users. Quarantining can also be paired with a reason. The command
 * will also try to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either quarantine other
 * users or to quarantine the specific given user (for example a moderator attempting to quarantine
 * an admin).
 */
public final class QuarantineCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(QuarantineCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "quarantine";
    private static final String ACTION_VERB = "quarantine";
    private final Predicate<String> hasRequiredRole;
    private final ModerationActionsStore actionsStore;
    private final Config config;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     * @param config the config to use for this
     */
    public QuarantineCommand(@NotNull ModerationActionsStore actionsStore, @NotNull Config config) {
        super(COMMAND_NAME,
                "Puts the given user under quarantine. They can not interact with anyone anymore then.",
                SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, TARGET_OPTION, "The user who you want to quarantine", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be quarantined",
                    true);

        this.config = config;
        hasRequiredRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static void handleAlreadyQuarantinedTarget(@NotNull IReplyCallback event) {
        event.reply("The user is already quarantined.").setEphemeral(true).queue();
    }

    private static RestAction<Boolean> sendDm(@NotNull ISnowflake target, @NotNull String reason,
            @NotNull Guild guild, @NotNull GenericEvent event) {
        String dmMessage =
                """
                        Hey there, sorry to tell you but unfortunately you have been put under quarantine in the server %s.
                        This means you can no longer interact with anyone in the server until you have been unquarantined again.
                        If you think this was a mistake, or the reason no longer applies, please contact a moderator or admin of the server.
                        The reason for the quarantine is: %s
                        """
                    .formatted(guild.getName(), reason);

        return event.getJDA()
            .openPrivateChannelById(target.getIdLong())
            .flatMap(channel -> channel.sendMessage(dmMessage))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull Member target,
            @NotNull Member author, @NotNull String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "\n(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.QUARANTINE,
                target.getUser(), dmNoticeText, reason);
    }

    private AuditableRestAction<Void> quarantineUser(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild) {
        logger.info("'{}' ({}) quarantined the user '{}' ({}) in guild '{}' for reason '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getUser().getAsTag(),
                target.getId(), guild.getName(), reason);

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.QUARANTINE, null, reason);

        return guild
            .addRoleToMember(target,
                    ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
            .reason(reason);
    }

    private void quarantineUserFlow(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild,
            @NotNull SlashCommandInteractionEvent event) {
        sendDm(target, reason, guild, event)
            .flatMap(hasSentDm -> quarantineUser(target, author, reason, guild)
                .map(result -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event::replyEmbeds)
            .queue();
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull IReplyCallback event) {
        if (!ModerationUtils.handleRoleChangeChecks(
                ModerationUtils.getQuarantinedRole(guild, config).orElse(null), ACTION_VERB, target,
                bot, author, guild, hasRequiredRole, reason, event)) {
            return false;
        }

        if (Objects.requireNonNull(target)
            .getRoles()
            .stream()
            .map(Role::getName)
            .anyMatch(ModerationUtils.getIsQuarantinedRolePredicate(config))) {
            handleAlreadyQuarantinedTarget(event);
            return false;
        }

        return true;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        Member target = event.getOption(TARGET_OPTION).getAsMember();
        Member author = event.getMember();
        String reason = event.getOption(REASON_OPTION).getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, target, reason, guild, event)) {
            return;
        }

        quarantineUserFlow(Objects.requireNonNull(target), author, reason, guild, event);
    }
}
