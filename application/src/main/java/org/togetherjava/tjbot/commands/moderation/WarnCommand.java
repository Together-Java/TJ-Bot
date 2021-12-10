package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.Result;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
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
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String ACTION_VERB = "warn";
    private final ModerationActionsStore actionsStore;
    private final Predicate<String> hasRequiredRole;

    /**
     * Creates a new Instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public WarnCommand(@NotNull ModerationActionsStore actionsStore) {
        super("warn", "warns the user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user to warn", true)
            .addOption(OptionType.STRING, REASON_OPTION, "The reason for the warning", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getHeavyModerationRolePattern())
            .asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static @NotNull RestAction<Boolean> dmUser(long userId, @NotNull String reason,
            @NotNull SlashCommandEvent event) {
        return event.getJDA()
            .openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, We are sorry to inform you that you have been warned for the following reason: %s
                            The repercussion of this warning depends on the severity of the warning or one the number of warnings you have received.\040
                            The warn system can be seen in the <#652440333107331082> channel.\040
                            """
                        .formatted(reason)))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull User target,
            @NotNull Member author, @NotNull String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationUtils.Action.WARN,
                target, dmNoticeText, reason);
    }

    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @NotNull CharSequence reason, @NotNull Guild guild, @NotNull Interaction event) {

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
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(USER_OPTION), "The option is null");
        User target = userOption.getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        Guild guild = Objects.requireNonNull(event.getGuild(), "The guild is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, reason, guild, event)) {
            return;
        }
        long userId = target.getIdLong();
        dmUser(userId, reason, event)
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event::replyEmbeds)
            .queue();

        actionsStore.addAction(guild.getIdLong(), author.getIdLong(), target.getIdLong(),
                ModerationUtils.Action.WARN, null, reason);
    }
}
