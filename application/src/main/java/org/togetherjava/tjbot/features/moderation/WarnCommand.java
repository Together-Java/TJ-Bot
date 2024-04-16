package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.logging.LogMarkers;

import javax.annotation.Nullable;

import java.util.Objects;

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
    private static final String ACTION_TITLE = "Warning";
    private final ModerationActionsStore actionsStore;

    /**
     * Creates a new instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public WarnCommand(ModerationActionsStore actionsStore) {
        super("warn", "Warns the given user", CommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to warn", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why you want to warn the user", true);

        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private void warnUserFlow(User target, Member author, String reason, Guild guild,
            SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        sendDm(target, reason, guild).map(hasSentDm -> {
            warnUser(target, author, reason, guild);
            return hasSentDm;
        })
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event.getHook()::sendMessageEmbeds)
            .queue();
    }

    private static RestAction<Boolean> sendDm(User target, String reason, Guild guild) {
        String description =
                "Hey there, sorry to tell you but unfortunately you have been warned in the server.";

        return ModerationUtils.sendModActionDm(
                ModerationUtils.getModActionEmbed(guild, ACTION_TITLE, description, reason, true),
                target);
    }

    private void warnUser(User target, Member author, String reason, Guild guild) {
        logger.info(LogMarkers.SENSITIVE, "'{}' ({}) warned the user '{}' ({}) for reason '{}'.",
                author.getUser().getName(), author.getId(), target.getName(), target.getId(),
                reason);

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.WARN, null, reason);
    }

    private static MessageEmbed sendFeedback(boolean hasSentDm, User target, Member author,
            String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.WARN, target,
                dmNoticeText, reason);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
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

        warnUserFlow(targetOption.getAsUser(), author, reason, guild, event);
    }

    private boolean handleChecks(Member bot, Member author, @Nullable Member target, String reason,
            SlashCommandInteractionEvent event) {
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }
        return ModerationUtils.handleReason(reason, event);
    }
}
