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
import org.togetherjava.tjbot.commands.moderation.ModerationUtils;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ModAuditLogGuildProcess;

import java.awt.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Routine that automatically checks moderator actions on a schedule and logs them to dedicated
 * channels. Use {@link #start()} to trigger automatic execution of the routine.
 * <p>
 * The routine is executed periodically, for example three times per day. When it runs, it checks
 * all moderator actions, such as user bans, kicks, muting or message deletion. Actions are then
 * logged to a dedicated channel, given by {@link Config#getModAuditLogChannelPattern()}.
 */
public final class ModAuditLogRoutine {
    private static final Logger logger = LoggerFactory.getLogger(ModAuditLogRoutine.class);
    private static final int CHECK_AUDIT_LOG_START_HOUR = 4;
    private static final int CHECK_AUDIT_LOG_EVERY_HOURS = 8;
    private static final int HOURS_OF_DAY = 24;
    private static final Color AMBIENT_COLOR = Color.decode("#4FC3F7");

    private final Predicate<TextChannel> isAuditLogChannel;
    private final Database database;
    private final JDA jda;
    private final ScheduledExecutorService checkAuditLogService =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Creates a new instance.
     *
     * @param jda the JDA instance to use to send messages and retrieve information
     * @param database the database for memorizing audit log dates
     */
    public ModAuditLogRoutine(@NotNull JDA jda, @NotNull Database database) {
        Predicate<String> isAuditLogChannelName =
                Pattern.compile(Config.getInstance().getModAuditLogChannelPattern())
                    .asMatchPredicate();
        isAuditLogChannel = channel -> isAuditLogChannelName.test(channel.getName());

        this.database = database;
        this.jda = jda;
    }

    private static @NotNull RestAction<MessageEmbed> handleAction(@NotNull Action action,
            @NotNull AuditLogEntry entry) {
        User author = Objects.requireNonNull(entry.getUser());
        return getTargetTagFromEntry(entry).map(targetTag -> createMessage(author, action,
                targetTag, entry.getReason(), entry.getTimeCreated()));
    }

    private static @NotNull MessageEmbed createMessage(@NotNull User author, @NotNull Action action,
            @NotNull String targetTag, @Nullable String reason,
            @NotNull TemporalAccessor timestamp) {
        String description = "%s **%s**.".formatted(action.getVerb(), targetTag);
        if (reason != null && !reason.isBlank()) {
            description += "\n\nReason: " + reason;
        }
        return new EmbedBuilder().setAuthor(author.getAsTag(), null, author.getAvatarUrl())
            .setTitle(action.getTitle())
            .setDescription(description)
            .setTimestamp(timestamp)
            .setColor(AMBIENT_COLOR)
            .build();
    }

    private static RestAction<String> getTargetTagFromEntry(@NotNull AuditLogEntry entry) {
        // If the target is null, the user got deleted in the meantime
        return entry.getJDA()
            .retrieveUserById(entry.getTargetIdLong())
            .map(target -> target == null ? "(user unknown)" : target.getAsTag());
    }

    private static boolean isSnowflakeAfter(@NotNull ISnowflake snowflake,
            @NotNull Instant timestamp) {
        return TimeUtil.getTimeCreated(snowflake.getIdLong()).toInstant().isAfter(timestamp);
    }

    /**
     * Schedules the given task for execution at a fixed rate (see
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
     * @param service the scheduler to use
     * @param command the command to schedule
     * @param periodStartHour the hour of the day that marks the start of this period
     * @param periodHours the scheduling period in hours
     * @return the instant when the command will be executed the first time
     */
    private static @NotNull Instant scheduleAtFixedRateFromNextFixedTime(
            @NotNull ScheduledExecutorService service, @NotNull Runnable command,
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
        service.scheduleAtFixedRate(command, ChronoUnit.SECONDS.between(now, nextFixedTime),
                TimeUnit.HOURS.toSeconds(periodHours), TimeUnit.SECONDS);
        return nextFixedTime;
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
        return Optional.of(handleAction(Action.BAN, entry));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleUnbanEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.UNBAN, entry));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleKickEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.KICK, entry));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleMuteEntry(
            @NotNull AuditLogEntry entry) {
        // NOTE Temporary mutes are realized as permanent mutes with automated unmute,
        // hence we can not differentiate a permanent or a temporary mute here
        return Optional.of(handleAction(Action.MUTE, entry));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleUnmuteEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.UNMUTE, entry));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleMessageDeleteEntry(
            @NotNull AuditLogEntry entry) {
        return Optional.of(handleAction(Action.MESSAGE_DELETION, entry));
    }

    /**
     * Starts the routine, automatically checking the audit logs on a schedule.
     */
    public void start() {
        // TODO This should be registered at some sort of routine system instead (see GH issue #235
        // which adds support for routines)
        Instant startInstant = scheduleAtFixedRateFromNextFixedTime(checkAuditLogService,
                this::checkAuditLogsRoutine, CHECK_AUDIT_LOG_START_HOUR,
                CHECK_AUDIT_LOG_EVERY_HOURS);
        logger.info("Checking audit logs is scheduled for {}.", startInstant);
    }

    private void checkAuditLogsRoutine() {
        logger.info("Checking audit logs of all guilds...");

        jda.getGuildCache().forEach(guild -> {
            if (!guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
                logger.error(
                        "The bot does not have 'VIEW_AUDIT_LOGS' permissions in guild '{}' which are required to log mod actions.",
                        guild.getName());
                return;
            }

            Optional<TextChannel> auditLogChannel = getModAuditLogChannel(guild);
            if (auditLogChannel.isEmpty()) {
                logger.warn(
                        "Unable to log moderation events, did not find a mod audit log channel matching the configured pattern '{}' for guild '{}'",
                        Config.getInstance().getModAuditLogChannelPattern(), guild.getName());
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
            .reduce((firstMessage, secondMessage) -> firstMessage.flatMap(result -> secondMessage))
            .ifPresent(RestAction::queue);

        database.write(context -> {
            var entry = context.newRecord(ModAuditLogGuildProcess.MOD_AUDIT_LOG_GUILD_PROCESS);
            entry.setGuildId(guildId);
            entry.setLastProcessedAuditLogEntry(updatedLogEntryTimestamp);

            if (entry.update() == 0) {
                entry.insert();
            }
        });
    }

    private static Optional<RestAction<Message>> handleAuditLog(
            @NotNull MessageChannel auditLogChannel, @NotNull AuditLogEntry entry) {
        Optional<RestAction<MessageEmbed>> maybeMessage = switch (entry.getType()) {
            case BAN -> handleBanEntry(entry);
            case UNBAN -> handleUnbanEntry(entry);
            case KICK -> handleKickEntry(entry);
            case MEMBER_ROLE_UPDATE -> handleRoleUpdateEntry(entry);
            case MESSAGE_DELETE -> handleMessageDeleteEntry(entry);
            default -> Optional.empty();
        };
        return maybeMessage.map(message -> message.flatMap(auditLogChannel::sendMessageEmbeds));
    }

    private static @NotNull Optional<RestAction<MessageEmbed>> handleRoleUpdateEntry(
            @NotNull AuditLogEntry entry) {
        if (containsMutedRole(entry, AuditLogKey.MEMBER_ROLES_ADD)) {
            return handleMuteEntry(entry);
        }
        if (containsMutedRole(entry, AuditLogKey.MEMBER_ROLES_REMOVE)) {
            return handleUnmuteEntry(entry);
        }
        return Optional.empty();
    }

    private static boolean containsMutedRole(@NotNull AuditLogEntry entry,
            @NotNull AuditLogKey key) {
        List<Map<String, String>> roleChanges = Optional.ofNullable(entry.getChangeByKey(key))
            .<List<Map<String, String>>>map(AuditLogChange::getNewValue)
            .orElse(List.of());
        return roleChanges.stream()
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .filter(changeEntry -> "name".equals(changeEntry.getKey()))
            .map(Map.Entry::getValue)
            .anyMatch(ModerationUtils.isMuteRole);
    }

    private Optional<TextChannel> getModAuditLogChannel(@NotNull Guild guild) {
        // Check cache first, then get full list
        return guild.getTextChannelCache()
            .stream()
            .filter(isAuditLogChannel)
            .findAny()
            .or(() -> guild.getTextChannels().stream().filter(isAuditLogChannel).findAny());
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
}
