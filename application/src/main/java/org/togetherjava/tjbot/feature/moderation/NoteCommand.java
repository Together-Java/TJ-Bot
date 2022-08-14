package org.togetherjava.tjbot.feature.moderation;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.feature.SlashCommandAdapter;
import org.togetherjava.tjbot.feature.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;


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
    private final Predicate<String> hasRequiredRole;

    /**
     * Creates a new instance.
     *
     * @param actionsStore used to store actions issued by this command
     * @param config the config to use for this
     */
    public NoteCommand(@NotNull ModerationActionsStore actionsStore, @NotNull Config config) {
        super("note", "Writes a note about the given user", SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, USER_OPTION, "The user who you want to write a note about",
                    true)
            .addOption(OptionType.STRING, CONTENT_OPTION,
                    "The content of the note you want to write", true);

        hasRequiredRole = Pattern.compile(config.getSoftModerationRolePattern()).asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        OptionMapping targetOption = event.getOption(USER_OPTION);
        Member author = event.getMember();
        Guild guild = event.getGuild();
        String content = event.getOption(CONTENT_OPTION).getAsString();

        if (!handleChecks(guild.getSelfMember(), author, targetOption.getAsMember(), content,
                event)) {
            return;
        }

        sendNote(targetOption.getAsUser(), author, content, guild, event);
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, CharSequence content, @NotNull IReplyCallback event) {
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }

        if (!ModerationUtils.handleHasAuthorRole(ACTION_VERB, hasRequiredRole, author, event)) {
            return false;
        }

        return ModerationUtils.handleReason(content, event);
    }

    private void sendNote(@NotNull User target, @NotNull Member author, @NotNull String content,
            @NotNull ISnowflake guild, @NotNull IReplyCallback event) {
        storeNote(target, author, content, guild);
        sendFeedback(target, author, content, event);
    }

    private void storeNote(@NotNull User target, @NotNull Member author, @NotNull String content,
            @NotNull ISnowflake guild) {
        logger.info("'{}' ({}) wrote a note about the user '{}' ({}) with content '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getAsTag(), target.getId(),
                content);

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationAction.NOTE, null, content);
    }

    private static void sendFeedback(@NotNull User target, @NotNull Member author,
            @NotNull String noteContent, @NotNull IReplyCallback event) {
        MessageEmbed feedback = ModerationUtils.createActionResponse(author.getUser(),
                ModerationAction.NOTE, target, null, noteContent);

        event.replyEmbeds(feedback).queue();
    }
}
