package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.logging.LogMarkers;

import java.util.Objects;

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
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public UnbanCommand(ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Unbans the given user from the server", CommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, TARGET_OPTION, "The banned user who you want to unban",
                    true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be unbanned", true);

        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private void unban(User target, Member author, String reason, Guild guild,
            IReplyCallback event) {
        guild.unban(target).reason(reason).queue(result -> {
            MessageEmbed message = ModerationUtils.createActionResponse(author.getUser(),
                    ModerationAction.UNBAN, target, null, reason);
            event.replyEmbeds(message).queue();

            logger.info(LogMarkers.SENSITIVE,
                    "'{}' ({}) unbanned the user '{}' ({}) from guild '{}' for reason '{}'.",
                    author.getUser().getName(), author.getId(), target.getName(), target.getId(),
                    guild.getName(), reason);

            actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                    ModerationAction.UNBAN, null, reason);
        }, unbanFailure -> handleFailure(unbanFailure, target, event));
    }

    private static void handleFailure(Throwable unbanFailure, User target, IReplyCallback event) {
        String targetTag = target.getName();
        if (unbanFailure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                event.reply("The specified user does not exist.").setEphemeral(true).queue();
                logger.debug(LogMarkers.SENSITIVE,
                        "Unable to unban the user '{}' because they do not exist.", targetTag);
                return;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                event.reply("The specified user is not banned.").setEphemeral(true).queue();
                logger.debug(LogMarkers.SENSITIVE,
                        "Unable to unban the user '{}' because they are not banned.", targetTag);
                return;
            }
        }

        event.reply("Sorry, but something went wrong.").setEphemeral(true).queue();
        logger.warn(LogMarkers.SENSITIVE,
                "Something unexpected went wrong while trying to unban the user '{}'.", targetTag,
                unbanFailure);
    }

    private boolean handleChecks(IPermissionHolder bot, Member author, CharSequence reason,
            Guild guild, IReplyCallback event) {
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
