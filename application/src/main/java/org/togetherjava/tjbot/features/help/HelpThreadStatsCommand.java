package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record1;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.jooq.impl.DSL.*;
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

    private final Database database;

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
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        long days = event.getOption(DURATION_OPTION) != null
                ? Objects.requireNonNull(event.getOption(DURATION_OPTION)).getAsLong()
                : 1;
        Instant startDate = Instant.now().minus(days, ChronoUnit.DAYS);

        event.deferReply().queue();

        database.read(context -> {
            var statsRecord = context
                .select(count().as(TOTAL_CREATED_FIELD), count()
                    .filterWhere(
                            HELP_THREADS.TICKET_STATUS.eq(HelpSystemHelper.TicketStatus.ACTIVE.val))
                    .as(OPEN_NOW_ALIAS),
                        count().filterWhere(HELP_THREADS.PARTICIPANTS.eq(1)).as(GHOST_NOW_ALIAS),
                        avg(HELP_THREADS.PARTICIPANTS).as(AVERAGE_PARTICIPANTS_ALIAS),
                        avg(HELP_THREADS.MESSAGE_COUNT).as(AVERAGE_MESSAGE_COUNT_ALIAS),
                        avg(durationInSeconds(HELP_THREADS.CLOSED_AT, HELP_THREADS.CREATED_AT))
                            .as(AVERAGE_THREAD_DURATION_IN_SECONDS_ALIAS),
                        min(durationInSeconds(HELP_THREADS.CLOSED_AT, HELP_THREADS.CREATED_AT))
                            .as(MINIMUM_THREAD_DURATION_IN_SECONDS_ALIAS),
                        max(durationInSeconds(HELP_THREADS.CLOSED_AT, HELP_THREADS.CREATED_AT))
                            .as(MAXIMUM_THREAD_DURATION_IN_SECONDS_ALIAS))
                .from(HELP_THREADS)
                .where(HELP_THREADS.CREATED_AT.ge(startDate))
                .fetchOne();

            if (statsRecord == null || statsRecord.get(TOTAL_CREATED_FIELD, Integer.class) == 0) {
                event.getHook()
                    .editOriginal("No stats available for the last " + days + " days.")
                    .queue();
                return null;
            }

            int totalCreated = statsRecord.get(TOTAL_CREATED_FIELD, Integer.class);
            int openThreads = statsRecord.get(OPEN_NOW_ALIAS, Integer.class);
            long ghostThreads = statsRecord.get(GHOST_NOW_ALIAS, Number.class).longValue();

            double rawResRate =
                    totalCreated > 0 ? ((double) (totalCreated - ghostThreads) / totalCreated) * 100
                            : 0;

            String highVolumeTag = getTopTag(context, startDate, count().desc());
            String highActivityTag =
                    getTopTag(context, startDate, avg(HELP_THREADS.MESSAGE_COUNT).desc());
            String lowActivityTag =
                    getTopTag(context, startDate, avg(HELP_THREADS.MESSAGE_COUNT).asc());

            String peakHourRange = getPeakHour(context, startDate);

            EmbedBuilder embed =
                    new EmbedBuilder().setTitle("📊 Help Thread Stats (Last " + days + " Days)")
                        .setColor(getStatusColor(totalCreated, ghostThreads))
                        .setTimestamp(Instant.now())
                        .setDescription("\u200B")
                        .setFooter("Together Java Community Stats",
                                Objects.requireNonNull(event.getGuild()).getIconUrl());

            embed.addField("📝 THREAD ACTIVITY",
                    "Created: `%d`%nCurrently Open: `%d`%nResponse Rate: %.1f%%%nPeak Hours: `%s`"
                        .formatted(totalCreated, openThreads, rawResRate, peakHourRange),
                    false);

            embed.addField("💬 ENGAGEMENT",
                    "Avg Messages: `%s`%nAvg Helpers: `%s`%nUnanswered (Ghost): `%d`".formatted(
                            formatDouble(Objects
                                .requireNonNull(statsRecord.get(AVERAGE_MESSAGE_COUNT_ALIAS))),
                            formatDouble(Objects
                                .requireNonNull(statsRecord.get(AVERAGE_PARTICIPANTS_ALIAS))),
                            ghostThreads),
                    false);

            embed.addField("🏷️ TAG ACTIVITY",
                    "Most Used: `%s`%nMost Active: `%s`%nNeeds Love: `%s`".formatted(highVolumeTag,
                            highActivityTag, lowActivityTag),
                    false);

            embed.addField("⚡ RESOLUTION SPEED",
                    "Average: `%s`%nFastest: `%s`%nSlowest: `%s`".formatted(
                            smartFormat(statsRecord.get(AVERAGE_THREAD_DURATION_IN_SECONDS_ALIAS,
                                    Double.class)),
                            smartFormat(statsRecord.get(MINIMUM_THREAD_DURATION_IN_SECONDS_ALIAS,
                                    Double.class)),
                            smartFormat(statsRecord.get(MAXIMUM_THREAD_DURATION_IN_SECONDS_ALIAS,
                                    Double.class))),
                    false);

            event.getHook().editOriginalEmbeds(embed.build()).queue();
            return null;
        });
    }

    private static Color getStatusColor(int totalCreated, long ghostThreads) {
        double rawResRate =
                totalCreated > 0 ? ((double) (totalCreated - ghostThreads) / totalCreated) * 100
                        : -1;

        if (rawResRate >= 70)
            return Color.GREEN;
        if (rawResRate >= 30)
            return Color.YELLOW;
        if (rawResRate >= 0)
            return Color.RED;
        return Color.GRAY;
    }

    private String getTopTag(DSLContext context, Instant start, OrderField<?> order) {
        return context.select(HELP_THREADS.TAGS)
            .from(HELP_THREADS)
            .where(HELP_THREADS.CREATED_AT.ge(start))
            .and(HELP_THREADS.TAGS.ne("none"))
            .groupBy(HELP_THREADS.TAGS)
            .orderBy(order)
            .limit(1)
            .fetchOptional(HELP_THREADS.TAGS)
            .orElse("N/A");
    }

    private String getPeakHour(DSLContext context, Instant start) {
        return context.select(field("strftime('%H', {0})", String.class, HELP_THREADS.CREATED_AT))
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
            .orElse("N/A");
    }

    private String smartFormat(Double seconds) {
        if (seconds < 0)
            return "N/A";
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
