package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.logging.LogMarkers;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * This command can kicks users. Kicking can also be paired with a kick reason. The command will
 * also try to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either kick other users or
 * to kick the specific given user (for example a moderator attempting to kick an admin).
 */
public final class KickCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "kick";
    private static final String ACTION_VERB = "kick";
    private static final String ACTION_TITLE = "Kick";
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public KickCommand(ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Kicks the given user from the server", CommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to kick", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be kicked", true);

        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static void handleAbsentTarget(IReplyCallback event) {
        event.reply("I can not kick the given user since they are not part of the guild anymore.")
            .setEphemeral(true)
            .queue();
    }

    private void kickUserFlow(Member target, Member author, String reason, Guild guild,
            SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        sendDm(target.getUser(), reason, guild)
            .flatMap(hasSentDm -> kickUser(target, author, reason, guild)
                .map(kickResult -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event.getHook()::sendMessageEmbeds)
            .queue();
    }

    private static RestAction<Boolean> sendDm(User target, String reason, Guild guild) {
        String description =
                """
                        Hey there, sorry to tell you but unfortunately you have been kicked from the server.
                        If you think this was a mistake, please contact a moderator or admin of this server.""";

        return ModerationUtils.sendModActionDm(
                ModerationUtils.getModActionEmbed(guild, ACTION_TITLE, description, reason, false),
                target);
    }

    private AuditableRestAction<Void> kickUser(Member target, Member author, String reason,
            Guild guild) {
        logger.info(LogMarkers.SENSITIVE,
                "'{}' ({}) kicked the user '{}' ({}) from guild '{}' for reason '{}'.",
                author.getUser().getName(), author.getId(), target.getUser().getName(),
                target.getId(), guild.getName(), reason);

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.KICK, null, reason);

        return guild.kick(target).reason(reason);
    }

    private static MessageEmbed sendFeedback(boolean hasSentDm, Member target, Member author,
            String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.KICK,
                target.getUser(), dmNoticeText, reason);
    }

    private boolean handleChecks(Member bot, Member author, @Nullable Member target,
            CharSequence reason, Guild guild, IReplyCallback event) {
        // Member doesn't exist if attempting to kick a user who is not part of the guild anymore.
        if (target == null) {
            handleAbsentTarget(event);
            return false;
        }
        if (!ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author, target, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasBotPermissions(ACTION_VERB, Permission.KICK_MEMBERS, bot,
                guild, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasAuthorPermissions(ACTION_VERB, Permission.KICK_MEMBERS,
                author, event)) {
            return false;
        }
        return ModerationUtils.handleReason(reason, event);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
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
        kickUserFlow(Objects.requireNonNull(target), author, reason, guild, event);
    }
}
