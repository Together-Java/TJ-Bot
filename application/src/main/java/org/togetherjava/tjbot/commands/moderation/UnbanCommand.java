package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Unbans a given user. Unbanning can also be paired with a reason. The command fails if the user is
 * currently not banned.
 */
public final class UnbanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "unban";
    private static final String ACTION_VERB = "unban";
    private final Predicate<String> hasRequiredRole;
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public UnbanCommand(@NotNull ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Unbans the given user from the server", CommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, TARGET_OPTION, "The banned user who you want to unban",
                    true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be unbanned", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getHeavyModerationRolePattern())
            .asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private void unban(@NotNull User target, @NotNull Member author, @NotNull String reason,
            @NotNull Guild guild, @NotNull IReplyCallback event) {
        guild.unban(target).reason(reason).queue(result -> {
            MessageEmbed message = ModerationUtils.createActionResponse(author.getUser(),
                    ModerationAction.UNBAN, target, null, reason);
            event.replyEmbeds(message).queue();

            logger.info("'{}' ({}) unbanned the user '{}' ({}) from guild '{}' for reason '{}'.",
                    author.getUser().getAsTag(), author.getId(), target.getAsTag(), target.getId(),
                    guild.getName(), reason);

            actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                    ModerationAction.UNBAN, null, reason);
        }, unbanFailure -> handleFailure(unbanFailure, target, event));
    }

    private static void handleFailure(@NotNull Throwable unbanFailure, @NotNull User target,
            @NotNull IReplyCallback event) {
        String targetTag = target.getAsTag();
        if (unbanFailure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                event.reply("The specified user does not exist.").setEphemeral(true).queue();
                logger.debug("Unable to unban the user '{}' because they do not exist.", targetTag);
                return;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                event.reply("The specified user is not banned.").setEphemeral(true).queue();
                logger.debug("Unable to unban the user '{}' because they are not banned.",
                        targetTag);
                return;
            }
        }

        event.reply("Sorry, but something went wrong.").setEphemeral(true).queue();
        logger.warn("Something unexpected went wrong while trying to unban the user '{}'.",
                targetTag, unbanFailure);
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion"})
    private boolean handleChecks(@NotNull IPermissionHolder bot, @NotNull Member author,
            @NotNull CharSequence reason, @NotNull Guild guild, @NotNull IReplyCallback event) {
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
        User target = Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null")
            .getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, reason, guild, event)) {
            return;
        }
        unban(target, author, reason, guild, event);
    }
}