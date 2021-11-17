package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.WarnSystem;
import org.togetherjava.tjbot.db.generated.tables.records.WarnSystemRecord;

import java.awt.*;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * This command allows a moderator to warn a user. This moderator will need to have ban permission
 * and well need to type {@code /warn @user reason}. If the command is successful the user will be
 * warned and will receive a dm telling them why there were warned. After that all the data will be
 * added to the database.
 */
public class WarnCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WarnCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String ACTION_VERB = "warn";
    private final Database database;
    private final Predicate<String> hasRequiredRole;

    /**
     * Creates a new Instance.
     */
    public WarnCommand(@NotNull Database database) {
        super("warn", "warns the user", SlashCommandVisibility.GUILD);
        this.database = database;

        getData().addOption(OptionType.USER, USER_OPTION, "The user to warn", true)
            .addOption(OptionType.STRING, REASON_OPTION, "The reason for the warning", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getSoftModerationRolePattern())
            .asMatchPredicate();
    }

    private static RestAction<Boolean> dmUser(long userId, @NotNull String reason,
            @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        return event.getJDA()
            .openPrivateChannelById(userId)
            .flatMap(privateChannel -> event.replyEmbeds(new EmbedBuilder().setTitle("Warned")
                .setDescription("You have been warned in " + guild.getName() + " for " + reason)
                .setColor(Color.decode("#895FE8"))
                .build()))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull User target,
            @NotNull Member author, @NotNull String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationUtils.Action.BAN,
                target, dmNoticeText, reason);
    }

    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull Interaction event) {

        // Member doesn't exist if attempting to ban a user who is not part of the guild.
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }

        if (!ModerationUtils.handleHasAuthorRole(ACTION_VERB, hasRequiredRole, author, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasBotPermissions(ACTION_VERB, Permission.KICK_MEMBERS, bot,
                guild, event)) {
            return false;
        }
        if (!ModerationUtils.handleHasAuthorPermissions(ACTION_VERB, Permission.KICK_MEMBERS,
                author, guild, event)) {
            return false;
        }
        return ModerationUtils.handleReason(reason, event);
    }

    /**
     * Handles {@code /warn user reason} command. Saves the value under the given user, given guild,
     * given reason and adds one to the number of warns the user has.
     * <p>
     * This command can only be used by users with the {@code KICK_MEMBERS} permission.
     *
     * @param event the event of the command
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(USER_OPTION), "The option is null");
        User target = userOption.getAsUser();
        Member targetMember = userOption.getAsMember();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        Guild guild = Objects.requireNonNull(event.getGuild(), "The guild is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, targetMember, reason, guild, event)) {
            return;
        }

        long userId = target.getIdLong();
        dmUser(userId, reason, guild, event)
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event::replyEmbeds)
            .queue();

        long guildId = guild.getIdLong();
        Optional<Integer> oldWarnAmount = database.read(context -> {
            try (var select = context.selectFrom(WarnSystem.WARN_SYSTEM)) {
                return Optional
                    .ofNullable(select.where(WarnSystem.WARN_SYSTEM.USERID.eq(userId)
                        .and(WarnSystem.WARN_SYSTEM.GUILD_ID.eq(guildId))).fetchOne())
                    .map(WarnSystemRecord::getWarningAmount);
            }
        });

        int newWarnAmount = oldWarnAmount.orElse(0) + 1;
        try {
            database.write(context -> {
                WarnSystemRecord warnSystemRecord = context.newRecord(WarnSystem.WARN_SYSTEM)
                    .setUserid(target.getIdLong())
                    .setGuildId(guildId)
                    .setWarnReason(reason)
                    .setIsWarned(true)
                    .setWarningAmount(newWarnAmount);
                if (warnSystemRecord.update() == 0) {
                    warnSystemRecord.insert();
                }
            });
            logger.info("Saved the user '{}' to the warn system.", target.getAsTag());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
