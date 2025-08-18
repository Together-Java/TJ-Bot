package org.togetherjava.tjbot.features.tophelper;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.jooq.Records;
import org.jooq.impl.DSL;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Service used to compute Top Helpers of a given time range, see
 * {@link #computeTopHelpersDescending(Guild, TimeRange)}.
 * <p>
 * Also offers utility to process or display Top Helper results.
 */
public final class TopHelpersService {
    private static final int TOP_HELPER_LIMIT = 18;
    private static final int MAX_USER_NAME_LIMIT = 15;

    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database the database containing the message records of top helpers
     */
    public TopHelpersService(Database database) {
        this.database = database;
    }

    /**
     * Stats for a single Top Helper, computed for a specific time range. See
     * {@link #computeTopHelpersDescending(Guild, TimeRange)}.
     * 
     * @param authorId ID of the Top Helper
     * @param messageLengths lengths of messages send in the time range, the more, the better
     */
    public record TopHelperStats(long authorId, BigDecimal messageLengths) {
    }


    /**
     * Represents a time range with a defined start and end.
     * 
     * @param start the inclusive start of the range
     * @param end the exclusive end of the range
     * @param description used for visual representation e.g., 'July 2025'
     */
    public record TimeRange(Instant start, Instant end, String description) {
        /**
         * Creates a time range representing the previous month (assuming UTC). For example if the
         * current month is April, this will return a time range for March.
         * 
         * @return time range for the previous month
         */
        public static TimeRange ofPreviousMonth() {
            Month previousMonth = Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).getMonth();
            return TimeRange.ofPastMonth(previousMonth);
        }

        /**
         * Creates a time range representing the given month (assuming UTC). If the month lies in
         * the future for the current year, the past year will be used instead. For example, if the
         * current month is April 2025:
         * <ul>
         * <li>{@code ofPastMonth(Month.APRIL)} returns April 2025</li>
         * <li>{@code ofPastMonth(Month.MARCH)} returns March 2025</li>
         * <li>{@code ofPastMonth(Month.JUNE)} returns June 2024</li>
         * </ul>
         * 
         * @param atMonth the month to represent
         * @return Time range representing the given month, either for the current or the previous
         *         year.
         */
        public static TimeRange ofPastMonth(Month atMonth) {
            ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);

            int atYear = now.getYear();
            // E.g. using November, while it is March 2022, should use November 2021
            if (atMonth.compareTo(now.getMonth()) > 0) {
                atYear--;
            }
            YearMonth atYearMonth = YearMonth.of(atYear, atMonth);

            Instant start = atYearMonth.atDay(1).atTime(LocalTime.MIN).toInstant(ZoneOffset.UTC);
            Instant end =
                    atYearMonth.atEndOfMonth().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
            String description = "%s %d"
                .formatted(atMonth.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US), atYear);

            return new TimeRange(start, end, description);
        }
    }

    /**
     * Computes the Top Helpers of the given time range.
     * 
     * @param guild to compute Top Helpers for
     * @param range of the time to compute results for
     * @return list of top helpers, descending with the user who helped the most first
     */
    public List<TopHelperStats> computeTopHelpersDescending(Guild guild, TimeRange range) {
        return database.read(context -> context
            .select(HELP_CHANNEL_MESSAGES.AUTHOR_ID, DSL.sum(HELP_CHANNEL_MESSAGES.MESSAGE_LENGTH))
            .from(HELP_CHANNEL_MESSAGES)
            .where(HELP_CHANNEL_MESSAGES.GUILD_ID.eq(guild.getIdLong())
                .and(HELP_CHANNEL_MESSAGES.SENT_AT.between(range.start(), range.end())))
            .groupBy(HELP_CHANNEL_MESSAGES.AUTHOR_ID)
            .orderBy(DSL.two().desc())
            .limit(TOP_HELPER_LIMIT)
            .fetch(Records.mapping(TopHelperStats::new)));
    }

    /**
     * Retrieves the member-data to a given list of top helpers.
     * <p>
     * The resulting list is in the same order as the given list and will contain {@code null} for
     * any Top Helper who is not member of the guild anymore.
     *
     * @param topHelpers the list of top helpers to retrieve member-data for
     * @param guild the guild the top helpers are members of
     * @return list of member-data for each top helper, same size and same order.
     */
    public static Task<List<Member>> retrieveTopHelperMembers(List<TopHelperStats> topHelpers,
            Guild guild) {
        List<Long> topHelperIds = topHelpers.stream().map(TopHelperStats::authorId).toList();
        return guild.retrieveMembersByIds(topHelperIds);
    }

    /**
     * Visual representation of the given Top Helpers as ASCII table. The table includes columns ID,
     * Name and Message lengths.
     *
     * @param topHelpers the list of top helpers to represent
     * @param members the list of member-data that lines up with the topHelpers, for example given
     *        by {@link #retrieveTopHelperMembers(List, Guild)}
     * @return ASCII table representing the Top Helpers
     */
    public static String asAsciiTableWithIds(Collection<TopHelperStats> topHelpers,
            Collection<? extends Member> members) {
        return asAsciiTable(topHelpers, members, true);
    }

    /**
     * Visual representation of the given Top Helpers as ASCII table. The table includes columns
     * Name and Message lengths.
     *
     * @param topHelpers the list of top helpers to represent
     * @param members the list of member-data that lines up with the topHelpers, for example given
     *        by {@link #retrieveTopHelperMembers(List, Guild)}
     * @return ASCII table representing the Top Helpers
     */
    public static String asAsciiTable(Collection<TopHelperStats> topHelpers,
            Collection<? extends Member> members) {
        return asAsciiTable(topHelpers, members, false);
    }

    private static String asAsciiTable(Collection<TopHelperStats> topHelpers,
            Collection<? extends Member> members, boolean includeIds) {
        Map<Long, Member> userIdToMember =
                members.stream().collect(Collectors.toMap(Member::getIdLong, Function.identity()));

        List<List<String>> topHelpersDataTable = topHelpers.stream()
            .map(topHelper -> topHelperToDataRow(topHelper,
                    userIdToMember.get(topHelper.authorId()), includeIds))
            .toList();

        return dataTableToString(topHelpersDataTable, includeIds);
    }

    /**
     * Visual representation of the given members name as it should be used for display purposes.
     * 
     * @param member to get name of, {@code null} will be represented with a placeholder
     * @return name of the user for display purposes
     */
    public static String getUsernameDisplay(@Nullable Member member) {
        return MessageUtils.abbreviate(member == null ? "UNKNOWN_USER" : member.getEffectiveName(),
                MAX_USER_NAME_LIMIT);
    }

    private static List<String> topHelperToDataRow(TopHelperStats topHelper,
            @Nullable Member member, boolean includeIds) {
        String id = Long.toString(topHelper.authorId());
        String name = getUsernameDisplay(member);
        String messageLengths = Long.toString(topHelper.messageLengths().longValue());

        if (includeIds) {
            return List.of(id, name, messageLengths);
        } else {
            return List.of(name, messageLengths);
        }
    }

    private static String dataTableToString(Collection<List<String>> dataTable,
            boolean includeIds) {
        List<ColumnSetting> settings = new ArrayList<>();
        if (includeIds) {
            settings.add(new ColumnSetting("ID", HorizontalAlign.RIGHT));
        }
        settings.add(new ColumnSetting("Name", HorizontalAlign.RIGHT));
        settings.add(new ColumnSetting("Message lengths", HorizontalAlign.RIGHT));
        return dataTableToAsciiTable(dataTable, settings);
    }

    private static String dataTableToAsciiTable(Collection<List<String>> dataTable,
            List<ColumnSetting> columnSettings) {
        IntFunction<String> headerToAlignment = i -> columnSettings.get(i).headerName();
        IntFunction<HorizontalAlign> indexToAlignment = i -> columnSettings.get(i).alignment();

        IntFunction<ColumnData<List<String>>> indexToColumn =
                i -> new Column().header(headerToAlignment.apply(i))
                    .dataAlign(indexToAlignment.apply(i))
                    .with(row -> row.get(i));

        List<ColumnData<List<String>>> columns =
                IntStream.range(0, columnSettings.size()).mapToObj(indexToColumn).toList();

        return AsciiTable.getTable(AsciiTable.BASIC_ASCII_NO_DATA_SEPARATORS, dataTable, columns);
    }

    private record ColumnSetting(String headerName, HorizontalAlign alignment) {
    }
}
