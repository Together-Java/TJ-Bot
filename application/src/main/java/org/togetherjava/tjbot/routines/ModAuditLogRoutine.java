package org.togetherjava.tjbot.routines;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction;
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.commands.moderation.ModerationUtils;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ModAuditLogGuildProcess;

import java.awt.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

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
    private static final int HOURS_OF_DAY = 24;
    private static final Color AMBIENT_COLOR = Color.decode("#4FC3F7");

    private final Database database;
    private final Config config;

    /**
     * Creates a new instance.
     *
     * @param database the database for memorizing audit log dates
     * @param config the config to use for this
     */
    public ModAuditLogRoutine(@NotNull Database database, @NotNull Config config) {
        this.config = config;
        this.database = database;
    }

    private static @NotNull RestAction<AuditLogMessage> handleAction(@NotNull Action action,
            @NotNull AuditLogEntry entry) {
        User author = Objects.requireNonNull(entry.getUser());
        return getTargetFromEntryOrNull(entry).map(target -> new AuditLogMessage(author, action,
                target, entry.getReason(), entry.getTimeCreated()));
    }

    private static RestAction<User> getTargetFromEntryOrNull(@NotNull AuditLogEntry entry) {
        return entry.getJDA().retrieveUserById(entry.getTargetIdLong()).onErrorMap(error -> null);
    }

    private static boolean isSnowflakeAfter(@NotNull ISnowflake snowflake,
            @NotNull Instant timestamp) {
        return TimeUtil.getTimeCreated(snowflake.getIdLong()).toInstant().isAfter(timestamp);
    }

    /**
     * Creates a schedule for execution at a fixed rate (see
     * {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}). The
     * initial first execution will be delayed to the next fixed time that matches the given period,
     * effectively making execution stable at fixed times of a day - regardless of when this method
     * was originally triggered.
     * <p>
     * For example, if the given period is 8 hours with a start hour of 4 o'clock, this leads to the
     * fixed execution times of 4:00, 12:00 and 20:00 each day. The first execution is then delayed
     * to the closest time in that schedule. For example, if triggered at 7:00, execution will
     * happen at 12:00 and then follow the schedule.
     * <p>
     * Execution will also correctly roll over to the next day, for example if the method is
     * triggered at 21:30, the next execution will be at 4:00 the following day.
     *
     * @param periodStartHour the hour of the day that marks the start of this period
     * @param periodHours the scheduling period in hours
     * @return the according schedule representing the planned execution
     */
    private static @NotNull Schedule scheduleAtFixedRateFromNextFixedTime(
            @SuppressWarnings("SameParameterValue") int periodStartHour,
            @SuppressWarnings("SameParameterValue") int periodHours) {
        // NOTE This scheduler could be improved, for example supporting arbitrary periods (not just
        // hour-based). Also, it probably does not correctly handle all date/time-quirks, for
        // example if a schedule would hit a time that does not exist for a specific date due to DST
        // or similar issues. Those are minor though and can be ignored for now.
        if (periodHours <= 0 || periodHours >= HOURS_OF_DAY) {
            throw new IllegalArgumentException(
                    "Schedule period must not be zero and must fit into a single day");
        }
        if (periodStartHour <= 0 || periodStartHour >= HOURS_OF_DAY) {
            throw new IllegalArgumentException(
                    "Schedule period start hour must be a valid hour of a day (0-23)");
        }

        // Compute fixed schedule hours
        List<Integer> fixedScheduleHours = new ArrayList<>();

        for (int hour = periodStartHour; hour < HOURS_OF_DAY; hour += periodHours) {
            fixedScheduleHours.add(hour);
        }

        Instant now = Instant.now();
        Instant nextFixedTime =
                computeClosestNextScheduleDate(now, fixedScheduleHours, periodHours);
        return new Schedule(ScheduleMode.FIXED_RATE, ChronoUnit.SECONDS.between(now, nextFixedTime),
                TimeUnit.HOURS.toSeconds(periodHours), TimeUnit.SECONDS);
    }

    private static @NotNull Instant computeClosestNextScheduleDate(@NotNull Instant instant,
            @NotNull List<Integer> scheduleHours, int periodHours) {
        OffsetDateTime offsetDateTime = instant.atOffset(ZoneOffset.UTC);
        BiFunction<OffsetDateTime, Integer, Instant> dateAtTime =
                (date, hour) -> date.with(LocalTime.of(hour, 0)).toInstant();

        // The instant is either before the given hours, in between, or after.
        // For latter, we roll the schedule over once to the next day
        List<Instant> scheduleDates = scheduleHours.stream()
            .map(hour -> dateAtTime.apply(offsetDateTime, hour))
            .collect(Collectors.toCollection(ArrayList::new));
        int rolloverHour =
                (scheduleHours.get(scheduleHours.size() - 1) + periodHours) % HOURS_OF_DAY;
        scheduleDates.add(dateAtTime.apply(offsetDateTime.plusDays(1), rolloverHour));

        return scheduleDates.stream()
            .filter(instant::isBefore)
            .min(Comparator.comparing(scheduleDate -> Duration.between(instant, scheduleDate)))
            .orElseThrow();
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleBanEntry(
            @NotNull AuditLogEntry entry) {
        // NOTE Temporary bans are realized as permanent bans with automated unban,
        // hence we can not differentiate a permanent or a temporary ban here
        return Optional.of(handleAction(Action.BAN, entry).map(AuditLogMessage::toEmbed));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleUnbanEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.UNBAN, entry).map(AuditLogMessage::toEmbed));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleKickEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.KICK, entry).map(AuditLogMessage::toEmbed));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleMuteEntry(
            @NotNull AuditLogEntry entry) {
        // NOTE Temporary mutes are realized as permanent mutes with automated unmute,
        // hence we can not differentiate a permanent or a temporary mute here
        return Optional.of(handleAction(Action.MUTE, entry).map(AuditLogMessage::toEmbed));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleUnmuteEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.UNMUTE, entry).map(AuditLogMessage::toEmbed));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleMessageDeleteEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.MESSAGE_DELETION, entry).map(message -> {
            if (message.target() != null && message.target().isBot()) {
                // Message deletions against bots should be skipped. Cancel action.
                return null;
            }
            return message.toEmbed();
        }));
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        checkAuditLogsRoutine(jda);
    }

    @Override
    public @NotNull Schedule createSchedule() {
        Schedule schedule = scheduleAtFixedRateFromNextFixedTime(CHECK_AUDIT_LOG_START_HOUR,
                CHECK_AUDIT_LOG_EVERY_HOURS);
        logger.info("Checking audit logs is scheduled for {}.",
                Instant.now().plus(schedule.initialDuration(), schedule.unit().toChronoUnit()));
        return schedule;
    }

    private void checkAuditLogsRoutine(@NotNull JDA jda) {
        logger.info("Checking audit logs of all guilds...");

        jda.getGuildCache().forEach(guild -> {
            if (!guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
                logger.error(
                        "The bot does not have 'VIEW_AUDIT_LOGS' permissions in guild '{}' which are required to log mod actions.",
                        guild.getName());
                return;
            }

            Optional<TextChannel> auditLogChannel =
                    ModAuditLogWriter.getAndHandleModAuditLogChannel(guild);
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

    private void handleAuditLogs(@NotNull MessageChannel auditLogChannel,
            @NotNull PaginationAction<? extends AuditLogEntry, AuditLogPaginationAction> auditLogAction,
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

    private Optional<RestAction<Message>> handleAuditLog(@NotNull MessageChannel auditLogChannel,
            @NotNull AuditLogEntry entry) {
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

    private @NotNull Optional<RestAction<MessageEmbed>> handleRoleUpdateEntry(
            @NotNull AuditLogEntry entry) {
        if (containsMutedRole(entry, AuditLogKey.MEMBER_ROLES_ADD)) {
            return handleMuteEntry(entry);
        }
        if (containsMutedRole(entry, AuditLogKey.MEMBER_ROLES_REMOVE)) {
            return handleUnmuteEntry(entry);
        }
        return Optional.empty();
    }

    private boolean containsMutedRole(@NotNull AuditLogEntry entry, @NotNull AuditLogKey key) {
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

        Action(@NotNull String title, @NotNull String verb) {
            this.title = title;
            this.verb = verb;
        }

        @NotNull
        String getTitle() {
            return title;
        }

        @NotNull
        String getVerb() {
            return verb;
        }
    }

    private record AuditLogMessage(@NotNull User author, @NotNull Action action,
            @Nullable User target, @Nullable String reason, @NotNull TemporalAccessor timestamp) {
        @NotNull
        MessageEmbed toEmbed() {
            String targetTag = target == null ? "(user unknown)" : target.getAsTag();
            String description = "%s **%s**.".formatted(action.getVerb(), targetTag);

            if (reason != null && !reason.isBlank()) {
                description += "\n\nReason: " + reason;
            }

            return new EmbedBuilder().setAuthor(author.getAsTag(), null, author.getAvatarUrl())
                .setDescription(description)
                .setTimestamp(timestamp)
                .setColor(AMBIENT_COLOR)
                .build();
        }
    }
}
