package org.togetherjava.tjbot.features.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import javax.annotation.Nullable;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.togetherjava.tjbot.db.generated.Tables.HELP_THREADS;

/**
 * Implements the '/help-thread-stats' command which provides analytical insights into the help
 * forum's activity over a specific duration.
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * /help-thread-stats duration-option: 7 Days
 * }
 * </pre>
 * <p>
 * The command aggregates data such as response rates, engagement metrics (messages/helpers), tag
 * popularity, and resolution speeds.
 */
public class HelpThreadStatsCommand extends SlashCommandAdapter {
    public static final String COMMAND_NAME = "help-thread-stats";
    public static final String DURATION_OPTION = "duration-option";
    private static final String TOTAL_CREATED_FIELD = "total_created";
    private static final String OPEN_NOW_ALIAS = "open_now";
    private static final String GHOST_NOW_ALIAS = "ghost_count";
    private static final String AVERAGE_PARTICIPANTS_ALIAS = "avg_parts";
    private static final String AVERAGE_MESSAGE_COUNT_ALIAS = "avg_msgs";
    private static final String AVERAGE_THREAD_DURATION_IN_SECONDS_ALIAS = "avg_sec";
    private static final String MINIMUM_THREAD_DURATION_IN_SECONDS_ALIAS = "min_sec";
    private static final String MAXIMUM_THREAD_DURATION_IN_SECONDS_ALIAS = "max_sec";

    private static final String EMOJI_CHART = "📊";
    private static final String EMOJI_MEMO = "📝";
    private static final String EMOJI_SPEECH_BUBBLE = "💬";
    private static final String EMOJI_LABEL = "🏷️";
    private static final String EMOJI_LIGHTNING = "⚡";

    private static final String EMBED_BLANK_LINE = "\u200B";
    private static final String WHITESPACE = " ";

    private final Database database;

    private static final Duration COOLDOWN_DURATION = Duration.ofMinutes(1);

    private final Cache<Long, Instant> cooldownCache;


    /**
     * Creates an instance of the command.
     *
     * @param database the database to fetch help thread metrics from
     */
    public HelpThreadStatsCommand(Database database) {
        super(COMMAND_NAME, "Display Help Thread Statistics", CommandVisibility.GUILD);

        OptionData durationOption = new OptionData(OptionType.INTEGER, DURATION_OPTION,
                "The time range for statistics", false)
            .addChoice("1 Day", 1)
            .addChoice("7 Days", 7)
            .addChoice("30 Days", 30)
            .addChoice("90 Days", 90)
            .addChoice("180 Days", 180);

        getData().addOptions(durationOption);
        this.database = database;
        this.cooldownCache =
                Caffeine.newBuilder().maximumSize(500).expireAfterWrite(COOLDOWN_DURATION).build();
    }

    private long getSecondsSinceLastUsage(long channelId, Instant now) {
        long secondsSinceLastUsage = 0;
        Instant lastUsage = this.cooldownCache.getIfPresent(channelId);
        if (lastUsage != null) {
            Duration elapsed = Duration.between(lastUsage, now);
            // to avoid displaying -1 when elapsed just crosses cooldown
            secondsSinceLastUsage = Math.max(0, COOLDOWN_DURATION.minus(elapsed).toSeconds());
        }
        return secondsSinceLastUsage;
    }

    private Optional<Record> getHelpThreadUsageStats(Instant statsDurationStartDate) {
        return database.read(context -> {
            var statsRecord = context
                .select(count().as(TOTAL_CREATED_FIELD), count()
                    .filterWhere(
                            HELP_THREADS.TICKET_STATUS.eq(HelpSystemHelper.TicketStatus.ACTIVE.val))
                    .as(OPEN_NOW_ALIAS),
                        count().filterWhere(HELP_THREADS.PARTICIPANTS.eq(0)).as(GHOST_NOW_ALIAS),
                        avg(HELP_THREADS.PARTICIPANTS).as(AVERAGE_PARTICIPANTS_ALIAS),
                        avg(HELP_THREADS.MESSAGE_COUNT).as(AVERAGE_MESSAGE_COUNT_ALIAS),
                        avg(durationInSeconds(HELP_THREADS.CLOSED_AT, HELP_THREADS.CREATED_AT))
                            .filterWhere(HELP_THREADS.PARTICIPANTS.gt(0))
                            .as(AVERAGE_THREAD_DURATION_IN_SECONDS_ALIAS),
                        min(durationInSeconds(HELP_THREADS.CLOSED_AT, HELP_THREADS.CREATED_AT))
                            .filterWhere(HELP_THREADS.PARTICIPANTS.gt(0))
                            .as(MINIMUM_THREAD_DURATION_IN_SECONDS_ALIAS),
                        max(durationInSeconds(HELP_THREADS.CLOSED_AT, HELP_THREADS.CREATED_AT))
                            .filterWhere(HELP_THREADS.PARTICIPANTS.gt(0))
                            .as(MAXIMUM_THREAD_DURATION_IN_SECONDS_ALIAS))
                .from(HELP_THREADS)
                .where(HELP_THREADS.CREATED_AT.ge(statsDurationStartDate))
                .fetchOne();

            if (statsRecord == null || statsRecord.get(TOTAL_CREATED_FIELD, Integer.class) == 0) {
                return Optional.empty();
            }
            return Optional.of(statsRecord);
        });
    }

    private record StatsReportData(long days, int totalCreated, int openThreads, long ghostThreads,
            double responseRate, String highVolumeTag, String highActivityTag,
            String lowActivityTag, String peakHourRange, Record rawStats

    ) {
    }

    private EmbedBuilder buildStatsEmbed(StatsReportData helpThreadStatsResults,
            String guildIconUrl, int daysBack) {
        EmbedBuilder helpThreadStatsEmbed = new EmbedBuilder()
            .setTitle(EMOJI_CHART + " Help Thread Stats (Last " + daysBack + " Days)")
            .setColor(getStatusColor(helpThreadStatsResults.totalCreated(),
                    helpThreadStatsResults.ghostThreads()))
            .setTimestamp(Instant.now())
            .setDescription(EMBED_BLANK_LINE)
            .setFooter("Together Java Community Stats", guildIconUrl);

        helpThreadStatsEmbed.addField(EMOJI_MEMO + WHITESPACE + "THREAD ACTIVITY",
                "Created: `%d`%nCurrently Open: `%d`%nResponse Rate: %.1f%%%nPeak Hours: `%s`"
                    .formatted(helpThreadStatsResults.totalCreated(),
                            helpThreadStatsResults.openThreads(),
                            helpThreadStatsResults.responseRate(),
                            helpThreadStatsResults.peakHourRange()),
                false);

        helpThreadStatsEmbed.addField(EMOJI_SPEECH_BUBBLE + WHITESPACE + "ENGAGEMENT",
                "Avg Messages: `%s`%nAvg Helpers: `%s`%nUnanswered (Ghost): `%d`".formatted(
                        formatDouble(Objects.requireNonNull(helpThreadStatsResults.rawStats()
                            .get(AVERAGE_MESSAGE_COUNT_ALIAS))),
                        formatDouble(Objects.requireNonNull(
                                helpThreadStatsResults.rawStats().get(AVERAGE_PARTICIPANTS_ALIAS))),
                        helpThreadStatsResults.ghostThreads),
                false);

        helpThreadStatsEmbed.addField(EMOJI_LABEL + WHITESPACE + "TAG ACTIVITY",
                "Most Used: `%s`%nMost Active: `%s`%nNeeds Love: `%s`".formatted(
                        helpThreadStatsResults.highVolumeTag(),
                        helpThreadStatsResults.highActivityTag(),
                        helpThreadStatsResults.lowActivityTag()),
                false);

        helpThreadStatsEmbed.addField(EMOJI_LIGHTNING + WHITESPACE + "RESOLUTION SPEED",
                "Average: `%s`%nFastest: `%s`%nSlowest: `%s`".formatted(
                        smartFormat(helpThreadStatsResults.rawStats()
                            .get(AVERAGE_THREAD_DURATION_IN_SECONDS_ALIAS, Double.class)),
                        smartFormat(helpThreadStatsResults.rawStats()
                            .get(MINIMUM_THREAD_DURATION_IN_SECONDS_ALIAS, Double.class)),
                        smartFormat(helpThreadStatsResults.rawStats()
                            .get(MAXIMUM_THREAD_DURATION_IN_SECONDS_ALIAS, Double.class))),
                false);
        return helpThreadStatsEmbed;
    }


    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        long channelId = event.getChannel().getIdLong();
        Instant now = Instant.now();
        long secondsSinceLastUsage = getSecondsSinceLastUsage(channelId, now);
        if (secondsSinceLastUsage != 0L) {
            event
                .reply("This command is on cooldown! Please wait " + secondsSinceLastUsage
                        + " more seconds.")
                .setEphemeral(true)
                .queue();
            return;
        }
        event.deferReply().queue();
        cooldownCache.put(channelId, now);
        int daysBackOption = Optional.ofNullable(event.getOption(DURATION_OPTION))
            .map(OptionMapping::getAsInt)
            .orElse(1);

        Instant startDate = Instant.now().minus(daysBackOption, ChronoUnit.DAYS);

        getHelpThreadUsageStats(startDate).ifPresentOrElse(stats -> {
            int totalCreated = stats.get(TOTAL_CREATED_FIELD, Integer.class);
            int openThreads = stats.get(OPEN_NOW_ALIAS, Integer.class);
            long ghostThreads = stats.get(GHOST_NOW_ALIAS, Number.class).longValue();

            double helpThreadInteractionRate =
                    totalCreated > 0 ? ((double) (totalCreated - ghostThreads) / totalCreated) * 100
                            : 0;

            String highVolumeTag = getTopTag(startDate, count().desc());
            String highActivityTag = getTopTag(startDate, avg(HELP_THREADS.MESSAGE_COUNT).desc());
            String lowActivityTag = getTopTag(startDate, avg(HELP_THREADS.MESSAGE_COUNT).asc());

            String peakHourRange = getPeakHour(startDate);
            StatsReportData fetchedStats = new StatsReportData(daysBackOption, totalCreated,
                    openThreads, ghostThreads, helpThreadInteractionRate, highVolumeTag,
                    highActivityTag, lowActivityTag, peakHourRange, stats);
            EmbedBuilder helpThreadStatsEmbed =
                    buildStatsEmbed(fetchedStats, event.getGuild().getIconUrl(), daysBackOption);

            event.getHook().editOriginalEmbeds(helpThreadStatsEmbed.build()).queue();

        }, () -> event.getHook()
            .editOriginal("No stats available for the last " + daysBackOption + " days.")
            .queue());
    }

    private static Color getStatusColor(int totalHelpThreadsCreated, long ghostThreads) {
        double helpThreadInteractionRate = totalHelpThreadsCreated > 0
                ? ((double) (totalHelpThreadsCreated - ghostThreads) / totalHelpThreadsCreated)
                        * 100
                : -1;

        if (helpThreadInteractionRate >= 70)
            return Color.GREEN;
        if (helpThreadInteractionRate >= 30)
            return Color.YELLOW;
        if (helpThreadInteractionRate >= 0)
            return Color.RED;
        return Color.GRAY;
    }

    private String getTopTag(Instant start, OrderField<?> order) {
        return database.read(context -> context.select(HELP_THREADS.TAGS)
            .from(HELP_THREADS)
            .where(HELP_THREADS.CREATED_AT.ge(start))
            .and(HELP_THREADS.TAGS.ne("none"))
            .groupBy(HELP_THREADS.TAGS)
            .orderBy(order)
            .limit(1)
            .fetchOptional(HELP_THREADS.TAGS)
            .orElse("N/A"));
    }

    private String getPeakHour(Instant start) {
        return database.read(context -> context
            .select(field("strftime('%H', {0})", String.class, HELP_THREADS.CREATED_AT))
            .from(HELP_THREADS)
            .where(HELP_THREADS.CREATED_AT.ge(start))
            .groupBy(field("strftime('%H', {0})", String.class, HELP_THREADS.CREATED_AT))
            .orderBy(count().desc())
            .limit(1)
            .fetchOptional(Record1::value1)
            .map(hour -> {
                int h = Integer.parseInt(hour);
                return "%02d:00 - %02d:00 UTC".formatted(h, (h + 1) % 24);
            })
            .orElse("N/A"));
    }

    private String smartFormat(@Nullable Double seconds) {
        if (seconds == null || seconds < 0) {
            return "N/A";
        }

        if (seconds < 60)
            return "%.0f secs".formatted(seconds);
        if (seconds < 3600)
            return "%.1f mins".formatted(seconds / 60.0);
        if (seconds < 86400)
            return "%.1f hrs".formatted(seconds / 3600.0);
        return "%.1f days".formatted(seconds / 86400.0);
    }


    private String formatDouble(Object val) {
        return val instanceof Number num ? "%.2f".formatted(num.doubleValue()) : "0.00";
    }

    /**
     * Calculates the duration in seconds between two timestamp fields. Uses SQLite unixepoch for
     * conversion.
     */
    private Field<Double> durationInSeconds(Field<Instant> end, Field<Instant> start) {
        return field("unixepoch({0}) - unixepoch({1})", Double.class, end, start);
    }
}
