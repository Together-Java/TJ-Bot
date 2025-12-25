package org.togetherjava.tjbot.features.moderation.audit;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction;
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ModAuditLogGuildProcess;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.features.moderation.ModerationUtils;

import javax.annotation.Nullable;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


/**
 * Routine that automatically checks moderator actions on a schedule and logs them to dedicated
 * channels.
 * <p>
 * The routine is executed periodically, for example three times per day. When it runs, it checks
 * all moderator actions, such as user bans, kicks, muting or message deletion. Actions are then
 * logged to a dedicated channel, given by {@link Config#getModAuditLogChannelPattern()}.
 */
public final class ModAuditLogRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(ModAuditLogRoutine.class);
    private static final int CHECK_AUDIT_LOG_START_HOUR = 4;
    private static final int CHECK_AUDIT_LOG_EVERY_HOURS = 8;
    private static final Color AMBIENT_COLOR = Color.decode("#4FC3F7");

    private final Database database;
    private final Config config;
    private final ModAuditLogWriter modAuditLogWriter;

    /**
     * Creates a new instance.
     *
     * @param database the database for memorizing audit log dates
     * @param config the config to use for this
     * @param modAuditLogWriter to log tag changes for audition
     */
    public ModAuditLogRoutine(Database database, Config config,
            ModAuditLogWriter modAuditLogWriter) {
        this.config = config;
        this.database = database;
        this.modAuditLogWriter = modAuditLogWriter;
    }

    private static RestAction<AuditLogMessage> handleAction(Action action, AuditLogEntry entry) {
        User author = Objects.requireNonNull(entry.getUser());
        return getTargetFromEntryOrNull(entry).map(target -> new AuditLogMessage(author, action,
                target, entry.getReason(), entry.getTimeCreated()));
    }

    private static RestAction<User> getTargetFromEntryOrNull(AuditLogEntry entry) {
        return entry.getJDA().retrieveUserById(entry.getTargetIdLong()).onErrorMap(error -> null);
    }

    private static boolean isSnowflakeAfter(ISnowflake snowflake, Instant timestamp) {
        return TimeUtil.getTimeCreated(snowflake.getIdLong()).toInstant().isAfter(timestamp);
    }

    private static Optional<RestAction<MessageEmbed>> handleBanEntry(AuditLogEntry entry) {
        // NOTE Temporary bans are realized as permanent bans with automated unban,
        // hence we can not differentiate a permanent or a temporary ban here
        return Optional.of(handleAction(Action.BAN, entry).map(AuditLogMessage::toEmbed));
    }

    private static Optional<RestAction<MessageEmbed>> handleUnbanEntry(AuditLogEntry entry) {
        return Optional.of(handleAction(Action.UNBAN, entry).map(AuditLogMessage::toEmbed));
    }

    private static Optional<RestAction<MessageEmbed>> handleKickEntry(AuditLogEntry entry) {
        return Optional.of(handleAction(Action.KICK, entry).map(AuditLogMessage::toEmbed));
    }

    private static Optional<RestAction<MessageEmbed>> handleMuteEntry(AuditLogEntry entry) {
        // NOTE Temporary mutes are realized as permanent mutes with automated unmute,
        // hence we can not differentiate a permanent or a temporary mute here
        return Optional.of(handleAction(Action.MUTE, entry).map(AuditLogMessage::toEmbed));
    }

    private static Optional<RestAction<MessageEmbed>> handleUnmuteEntry(AuditLogEntry entry) {
        return Optional.of(handleAction(Action.UNMUTE, entry).map(AuditLogMessage::toEmbed));
    }

    private static Optional<RestAction<MessageEmbed>> handleMessageDeleteEntry(
            AuditLogEntry entry) {
        return Optional.of(handleAction(Action.MESSAGE_DELETION, entry).map(message -> {
            if (message.target() != null && message.target().isBot()) {
                // Message deletions against bots should be skipped. Cancel action.
                return null;
            }
            return message.toEmbed();
        }));
    }

    @Override
    public void runRoutine(JDA jda) {
        checkAuditLogsRoutine(jda);
    }

    @Override
    public Schedule createSchedule() {
        Schedule schedule = Schedule.atFixedRateFromNextFixedTime(CHECK_AUDIT_LOG_START_HOUR,
                CHECK_AUDIT_LOG_EVERY_HOURS);
        logger.info("Checking audit logs is scheduled for {}.",
                Instant.now().plus(schedule.initialDuration(), schedule.unit().toChronoUnit()));
        return schedule;
    }

    private void checkAuditLogsRoutine(JDA jda) {
        logger.info("Checking audit logs of all guilds...");

        jda.getGuildCache().forEach(guild -> {
            if (!guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
                logger.error(
                        "The bot does not have 'VIEW_AUDIT_LOGS' permissions in guild '{}' which are required to log mod actions.",
                        guild.getName());
                return;
            }

            Optional<TextChannel> auditLogChannel =
                    modAuditLogWriter.getAndHandleModAuditLogChannel(guild);
            if (auditLogChannel.isEmpty()) {
                return;
            }

            // NOTE Checking the audit log is subject to heavy rate limitations.
            handleAuditLogs(auditLogChannel.orElseThrow(), guild.retrieveAuditLogs(),
                    guild.getIdLong());
        });

        logger.info(
                "Finished checking audit logs of all guilds. The next check is scheduled to be executed in {} hours.",
                CHECK_AUDIT_LOG_EVERY_HOURS);
    }

    private void handleAuditLogs(MessageChannel auditLogChannel,
            PaginationAction<? extends AuditLogEntry, AuditLogPaginationAction> auditLogAction,
            long guildId) {
        Instant lastAuditLogEntryTimestamp =
                database.read(context -> Optional
                    .ofNullable(context.fetchOne(
                            ModAuditLogGuildProcess.MOD_AUDIT_LOG_GUILD_PROCESS,
                            ModAuditLogGuildProcess.MOD_AUDIT_LOG_GUILD_PROCESS.GUILD_ID
                                .eq(guildId)))
                    .map(entry -> entry.get(
                            ModAuditLogGuildProcess.MOD_AUDIT_LOG_GUILD_PROCESS.LAST_PROCESSED_AUDIT_LOG_ENTRY))
                    .orElse(Instant.now()));
        // NOTE This is a minor race condition. By taking the time before the actual lookup we
        // ensure that we do not miss anything but instead it is possible to receive an
        // action twice in such a rare case, which is okay.
        Instant updatedLogEntryTimestamp = Instant.now();

        // All entries after last lookup, chronologically ascending
        auditLogAction.stream()
            .takeWhile(entry -> isSnowflakeAfter(entry, lastAuditLogEntryTimestamp))
            .sorted(Comparator.comparing(TimeUtil::getTimeCreated))
            .map(entry -> handleAuditLog(auditLogChannel, entry))
            .flatMap(Optional::stream)
            .forEach(RestAction::queue);

        database.write(context -> {
            var entry = context.newRecord(ModAuditLogGuildProcess.MOD_AUDIT_LOG_GUILD_PROCESS);
            entry.setGuildId(guildId);
            entry.setLastProcessedAuditLogEntry(updatedLogEntryTimestamp);

            if (entry.update() == 0) {
                entry.insert();
            }
        });
    }

    private Optional<RestAction<Message>> handleAuditLog(MessageChannel auditLogChannel,
            AuditLogEntry entry) {
        Optional<RestAction<MessageEmbed>> maybeMessage = switch (entry.getType()) {
            case BAN -> handleBanEntry(entry);
            case UNBAN -> handleUnbanEntry(entry);
            case KICK -> handleKickEntry(entry);
            case MEMBER_ROLE_UPDATE -> handleRoleUpdateEntry(entry);
            case MESSAGE_DELETE -> handleMessageDeleteEntry(entry);
            default -> Optional.empty();
        };
        // It can have 3 states:
        // * empty optional - entry is irrelevant and should not be logged
        // * has RestAction but that will contain null - entry was relevant at first, but at
        // query-time we found out that it is irrelevant
        // * has RestAction but will contain a message - entry is relevant, log the message
        return maybeMessage
            .map(message -> message.flatMap(Objects::nonNull, auditLogChannel::sendMessageEmbeds));
    }

    private Optional<RestAction<MessageEmbed>> handleRoleUpdateEntry(AuditLogEntry entry) {
        if (containsMutedRole(entry, AuditLogKey.MEMBER_ROLES_ADD)) {
            return handleMuteEntry(entry);
        }
        if (containsMutedRole(entry, AuditLogKey.MEMBER_ROLES_REMOVE)) {
            return handleUnmuteEntry(entry);
        }
        return Optional.empty();
    }

    private boolean containsMutedRole(AuditLogEntry entry, AuditLogKey key) {
        List<Map<String, String>> roleChanges = Optional.ofNullable(entry.getChangeByKey(key))
            .<List<Map<String, String>>>map(AuditLogChange::getNewValue)
            .orElse(List.of());
        return roleChanges.stream()
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .filter(changeEntry -> "name".equals(changeEntry.getKey()))
            .map(Map.Entry::getValue)
            .anyMatch(ModerationUtils.getIsMutedRolePredicate(config));
    }

    private enum Action {
        BAN("Ban", "banned"),
        UNBAN("Unban", "unbanned"),
        KICK("Kick", "kicked"),
        MUTE("Mute", "muted"),
        UNMUTE("Unmute", "unmuted"),
        MESSAGE_DELETION("Message Deletion", "deleted messages from");

        private final String title;
        private final String verb;

        Action(String title, String verb) {
            this.title = title;
            this.verb = verb;
        }

        String getTitle() {
            return title;
        }

        String getVerb() {
            return verb;
        }
    }

    private record AuditLogMessage(User author, Action action, @Nullable User target,
            @Nullable String reason, TemporalAccessor timestamp) {
        MessageEmbed toEmbed() {
            String targetTag = target == null ? "(user unknown)" : target.getName();
            String description = "%s **%s**.".formatted(action.getVerb(), targetTag);

            if (reason != null && !reason.isBlank()) {
                description += "\n\nReason: " + reason;
            }

            String avatarOrDefaultUrl = author.getEffectiveAvatarUrl();

            return new EmbedBuilder().setAuthor(author.getName(), null, avatarOrDefaultUrl)
                .setDescription(description)
                .setTimestamp(timestamp)
                .setColor(AMBIENT_COLOR)
                .build();
        }
    }
}
