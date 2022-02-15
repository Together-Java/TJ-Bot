package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.Result;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * This command can warn users. The command will also try to DM the user to inform them about the
 * action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either warn other users or
 * to warn the specific given user (for example a moderator attempting to warn an admin).
 */
public final class WarnCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WarnCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String ACTION_VERB = "warn";
    private final ModerationActionsStore actionsStore;
    private final Predicate<String> hasRequiredRole;

    /**
     * Creates a new instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public WarnCommand(@NotNull ModerationActionsStore actionsStore) {
        super("warn", "Warns the given user", CommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to warn", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why you want to warn the user", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getHeavyModerationRolePattern())
            .asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private @NotNull RestAction<InteractionHook> warnUserFlow(@NotNull User target,
            @NotNull Member author, @NotNull String reason, @NotNull Guild guild,
            @NotNull SlashCommandInteractionEvent event) {
        return dmUser(target, reason, guild, event).map(hasSentDm -> {
            warnUser(target, author, reason, guild);
            return hasSentDm;
        })
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event::replyEmbeds);
    }

    private static @NotNull RestAction<Boolean> dmUser(@NotNull ISnowflake target,
            @NotNull String reason, @NotNull Guild guild,
            @NotNull SlashCommandInteractionEvent event) {
        return event.getJDA()
            .openPrivateChannelById(target.getId())
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been warned in the server %s.
                            If you think this was a mistake, please contact a moderator or admin of the server.
                            The reason for the warning is: %s
                            """
                        .formatted(guild.getName(), reason)))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private void warnUser(@NotNull User target, @NotNull Member author, @NotNull String reason,
            @NotNull Guild guild) {
        logger.info("'{}' ({}) warned the user '{}' ({}) for reason '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getAsTag(), target.getId(),
                reason);

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.WARN, null, reason);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull User target,
            @NotNull Member author, @NotNull String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.WARN, target,
                dmNoticeText, reason);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(USER_OPTION), "The target is null");
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        Guild guild = Objects.requireNonNull(event.getGuild(), "The guild is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        if (!handleChecks(guild.getSelfMember(), author, targetOption.getAsMember(), reason,
                event)) {
            return;
        }

        warnUserFlow(targetOption.getAsUser(), author, reason, guild, event).queue();
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, String reason, @NotNull SlashCommandInteractionEvent event) {
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasAuthorRole(ACTION_VERB, hasRequiredRole, author, event)) {
            return false;
        }
        return ModerationUtils.handleReason(reason, event);
    }
}
