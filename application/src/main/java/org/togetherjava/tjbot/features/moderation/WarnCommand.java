package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

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
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String ACTION_VERB = "warn";
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

        event.deferReply()
            .queue(hook -> ModerationUtils
                .warnUser(guild, targetOption.getAsUser(), author, reason, actionsStore)
                .flatMap(hook::sendMessageEmbeds)
                .queue());
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
