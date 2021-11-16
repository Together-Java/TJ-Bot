package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.Result;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.KickSystem;
import org.togetherjava.tjbot.db.generated.tables.records.KickSystemRecord;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;


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
    private final Database database;
    private final Predicate<String> hasRequiredRole;

    /**
     * Constructs an instance.
     * 
     * @param database used to store the kicks in the database
     */
    public KickCommand(Database database) {
        super(COMMAND_NAME, "Kicks the given user from the server", SlashCommandVisibility.GUILD);
        this.database = database;

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to kick", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be kicked", true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getSoftModerationRolePattern())
            .asMatchPredicate();
    }

    private static void handleAbsentTarget(@NotNull Interaction event) {
        event.reply("I can not kick the given user since they are not part of the guild anymore.")
            .setEphemeral(true)
            .queue();
    }

    private static void kickUserFlow(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        sendDm(target, reason, guild, event)
            .flatMap(hasSentDm -> kickUser(target, author, reason, guild)
                .map(kickResult -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event::replyEmbeds)
            .queue();
    }

    private static RestAction<Boolean> sendDm(@NotNull ISnowflake target, @NotNull String reason,
            @NotNull Guild guild, @NotNull GenericEvent event) {
        return event.getJDA()
            .openPrivateChannelById(target.getId())
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been kicked from the server %s.
                            If you think this was a mistake, please contact a moderator or admin of the server.
                            The reason for the kick is: %s
                            """
                        .formatted(guild.getName(), reason)))
            .mapToResult()
            .map(Result::isSuccess);
    }

    private static AuditableRestAction<Void> kickUser(@NotNull Member target,
            @NotNull Member author, @NotNull String reason, @NotNull Guild guild) {
        logger.info("'{}' ({}) kicked the user '{}' ({}) from guild '{}' for reason '{}'.",
                author.getUser().getAsTag(), author.getId(), target.getUser().getAsTag(),
                target.getId(), guild.getName(), reason);

        return guild.kick(target, reason).reason(reason);
    }

    private static @NotNull MessageEmbed sendFeedback(boolean hasSentDm, @NotNull Member target,
            @NotNull Member author, @NotNull String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationUtils.Action.KICK,
                target.getUser(), dmNoticeText, reason);
    }

    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion", "MethodWithTooManyParameters"})
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull CharSequence reason, @NotNull Guild guild,
            @NotNull Interaction event) {
        // Member doesn't exist if attempting to kick a user who is not part of the guild anymore.
        if (target == null) {
            handleAbsentTarget(event);
            return false;
        }
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

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
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

        try {
            database.write(context -> {
                KickSystemRecord kickSystemRecord = context.newRecord(KickSystem.KICK_SYSTEM)
                    .setUserid(target.getUser().getIdLong())
                    .setAuthorId(author.getIdLong())
                    .setGuildId(guild.getIdLong())
                    .setKickReason(reason);
                if (kickSystemRecord.update() == 0) {
                    kickSystemRecord.insert();
                }
            });
            logger.info("Saved the user '{}' to the kick system.", target.getUser().getAsTag());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
