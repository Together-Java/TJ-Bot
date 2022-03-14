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
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * This command can unquarantine quarantined users. Unquarantining can also be paired with a reason.
 * The command will also try to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either unquarantine other
 * users or to unquarantine the specific given user (for example a moderator attempting to
 * unquarantine an admin).
 */
public final class UnquarantineCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnquarantineCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "unquarantine";
    private static final String ACTION_VERB = "unquarantine";
    private final Predicate<String> hasRequiredRole;
    private final ModerationActionsStore actionsStore;
    private final Config config;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     * @param config the config to use for this
     */
    public UnquarantineCommand(@NotNull ModerationActionsStore actionsStore,
            @NotNull Config config) {
        super(COMMAND_NAME,
                "Unquarantines the given already quarantined user so that they can interact again",
                SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, TARGET_OPTION, "The user who you want to unquarantine",
                    true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be unquarantined",
                    true);

        this.config = config;
        hasRequiredRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static void handleNotQuarantinedTarget(@NotNull Interaction event) {
        event.reply("The user is not quarantined.").setEphemeral(true).queue();
    }

    private static RestAction<Boolean> sendDm(@NotNull ISnowflake target, @NotNull String reason,
            @NotNull Guild guild, @NotNull GenericEvent event) {
        String dmMessage = """
                Hey there, you have been put out of quarantine in the server %s.
                This means you can now interact with others in the server again.
                The reason for the unquarantine is: %s
                """.formatted(guild.getName(), reason);

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
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.UNQUARANTINE,
                target.getUser(), dmNoticeText, reason);
    }

    private AuditableRestAction<Void> unquarantineUser(@NotNull Member target,
            @NotNull Member author, @NotNull String reason, @NotNull Guild guild) {
        logger.info("'{}' ({}) unquarantined the user '{}' ({}) in guild '{}' for reason '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getUser().getAsTag(),
                target.getId(), guild.getName(), reason);

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.UNQUARANTINE, null, reason);

        return guild
            .removeRoleFromMember(target,
                    ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
            .reason(reason);
    }

    private void unquarantineUserFlow(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        sendDm(target, reason, guild, event)
            .flatMap(hasSentDm -> unquarantineUser(target, author, reason, guild)
                .map(result -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event::replyEmbeds)
            .queue();
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull Interaction event) {
        if (!ModerationUtils.handleRoleChangeChecks(
                ModerationUtils.getQuarantinedRole(guild, config).orElse(null), ACTION_VERB, target,
                bot, author, guild, hasRequiredRole, reason, event)) {
            return false;
        }

        if (Objects.requireNonNull(target)
            .getRoles()
            .stream()
            .map(Role::getName)
            .noneMatch(ModerationUtils.getIsQuarantinedRolePredicate(config))) {
            handleNotQuarantinedTarget(event);
            return false;
        }

        return true;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member target = event.getOption(TARGET_OPTION).getAsMember();
        Member author = event.getMember();
        String reason = event.getOption(REASON_OPTION).getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, target, reason, guild, event)) {
            return;
        }
        unquarantineUserFlow(Objects.requireNonNull(target), author, reason, guild, event);
    }
}
