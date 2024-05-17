package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.moderation.audit.AuditCommand;
import org.togetherjava.tjbot.logging.LogMarkers;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * This command allows users to write notes about others. Notes are persisted and can be retrieved
 * using {@link AuditCommand}, like other moderative actions.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either write a note about
 * other users or to write a note about the specific given user (for example a moderator attempting
 * to write a note about an admin).
 */
public final class NoteCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(NoteCommand.class);
    private static final String USER_OPTION = "user";
    private static final String CONTENT_OPTION = "content";
    private static final String ACTION_VERB = "write a note about";
    private final ModerationActionsStore actionsStore;

    /**
     * Creates a new instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public NoteCommand(ModerationActionsStore actionsStore) {
        super("note", "Writes a note about the given user", CommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, USER_OPTION, "The user who you want to write a note about",
                    true)
            .addOption(OptionType.STRING, CONTENT_OPTION,
                    "The content of the note you want to write", true);

        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(USER_OPTION), "The user is null");
        Member author = Objects.requireNonNull(event.getMember());
        Guild guild = Objects.requireNonNull(event.getGuild());
        String content =
                Objects.requireNonNull(event.getOption(CONTENT_OPTION), "The content is null")
                    .getAsString();

        if (!handleChecks(guild.getSelfMember(), author, targetOption.getAsMember(), content,
                event)) {
            return;
        }

        sendNote(targetOption.getAsUser(), author, content, guild, event);
    }

    private boolean handleChecks(Member bot, Member author, @Nullable Member target,
            CharSequence content, IReplyCallback event) {
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }

        return ModerationUtils.handleReason(content, event);
    }

    private void sendNote(User target, Member author, String content, ISnowflake guild,
            IReplyCallback event) {
        storeNote(target, author, content, guild);
        sendFeedback(target, author, content, event);
    }

    private void storeNote(User target, Member author, String content, ISnowflake guild) {
        logger.info(LogMarkers.SENSITIVE,
                "'{}' ({}) wrote a note about the user '{}' ({}) with content '{}'.",
                author.getUser().getName(), author.getId(), target.getName(), target.getId(),
                content);

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.NOTE, null, content);
    }

    private static void sendFeedback(User target, Member author, String noteContent,
            IReplyCallback event) {
        MessageEmbed feedback = ModerationUtils.createActionResponse(author.getUser(),
                ModerationAction.NOTE, target, null, noteContent);

        event.replyEmbeds(feedback).queue();
    }
}
