package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
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

    /**
     * Handles {@code /warn user reason} command. Saves the value under the given user, given guild,
     * given reason and add +1 to the number of warns the user has.
     * <p>
     * This command can only be used by users with the {@code KICK_MEMBERS} permission.
     *
     * @param event the event of the command
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(USER_OPTION), "The user is null");
        User target = userOption.getAsUser();
        Member targetMember = userOption.getAsMember();
        Member author = userOption.getAsMember();
        Guild guild = Objects.requireNonNull(event.getGuild());
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        if (!handleChecks(guild.getSelfMember(), author, targetMember, reason, guild, event)) {
            return;
        }

        long userId = target.getIdLong();
        dmUser(event.getJDA(), userId, reason, guild);

        long guildId = guild.getIdLong();
        Optional<Integer> oldWarnAmount = database.read(context -> {
            try (var select = context.selectFrom(WarnSystem.WARN_SYSTEM)) {
                return Optional
                    .ofNullable(select.where(WarnSystem.WARN_SYSTEM.USERID.eq(target.getIdLong())
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
                    .setWarningAmount(newWarnAmount);
                if (warnSystemRecord.update() == 0) {
                    warnSystemRecord.insert();
                }
            });
            logger.info("Saved the user '{}' to the ban system.", target.getAsTag());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void dmUser(@NotNull JDA jda, long userId, @NotNull String reason,
            @NotNull Guild guild) {
        jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Warn")
                .setDescription("You have been warned in " + guild.getName() + " for " + reason)
                .setColor(Color.MAGENTA)
                .build()))
            .queue();
    }

    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull Interaction event) {
        // Member doesn't exist if attempting to kick a user who is not part of the guild anymore.
        if (!ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author, target, event)) {
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
}
