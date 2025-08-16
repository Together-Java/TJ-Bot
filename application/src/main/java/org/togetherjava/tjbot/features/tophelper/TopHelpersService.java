package org.togetherjava.tjbot.features.tophelper;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.concurrent.Task;

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

// TODO Javadoc everywhere
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

    public List<TopHelperResult> computeTopHelpersDescending(long guildId, Instant start,
            Instant end) {
        // TODO Undo after testing!
        /*
         * return database.read(context -> context .select(HELP_CHANNEL_MESSAGES.AUTHOR_ID,
         * DSL.sum(HELP_CHANNEL_MESSAGES.MESSAGE_LENGTH)) .from(HELP_CHANNEL_MESSAGES)
         * .where(HELP_CHANNEL_MESSAGES.GUILD_ID.eq(guildId)
         * .and(HELP_CHANNEL_MESSAGES.SENT_AT.between(start, end)))
         * .groupBy(HELP_CHANNEL_MESSAGES.AUTHOR_ID) .orderBy(DSL.two().desc())
         * .limit(TOP_HELPER_LIMIT) .fetch(Records.mapping(TopHelperResult::new)));
         */
        return List.of(new TopHelperResult(1014989258165072028L, BigDecimal.valueOf(500)),
                new TopHelperResult(257500867568205824L, BigDecimal.valueOf(400)),
                new TopHelperResult(238042761490595843L, BigDecimal.valueOf(300)),
                new TopHelperResult(905767721814351892L, BigDecimal.valueOf(200)),
                new TopHelperResult(157994153806921728L, BigDecimal.valueOf(100)));
    }

    public Task<List<Member>> retrieveTopHelperMembers(List<TopHelperResult> topHelpers,
            Guild guild) {
        List<Long> topHelperIds = topHelpers.stream().map(TopHelperResult::authorId).toList();
        return guild.retrieveMembersByIds(topHelperIds);
    }

    public String asAsciiTable(Collection<TopHelperResult> topHelpers,
            Collection<? extends Member> members, boolean includeIds) {
        Map<Long, Member> userIdToMember =
                members.stream().collect(Collectors.toMap(Member::getIdLong, Function.identity()));

        List<List<String>> topHelpersDataTable = topHelpers.stream()
            .map(topHelper -> topHelperToDataRow(topHelper,
                    userIdToMember.get(topHelper.authorId()), includeIds))
            .toList();

        return dataTableToString(topHelpersDataTable, includeIds);
    }

    public static String getUsernameDisplay(@Nullable Member member) {
        return MessageUtils.abbreviate(member == null ? "UNKNOWN_USER" : member.getEffectiveName(),
                MAX_USER_NAME_LIMIT);
    }

    private static List<String> topHelperToDataRow(TopHelpersService.TopHelperResult topHelper,
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
            settings.add(new ColumnSetting("Id", HorizontalAlign.RIGHT));
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

    public record TopHelperResult(long authorId, BigDecimal messageLengths) {
    }

    public record TimeRange(Instant start, Instant end, String description) {
        public static TimeRange ofPreviousMonth() {
            Month previousMonth = Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).getMonth();
            return TimeRange.ofMonth(previousMonth);
        }

        public static TimeRange ofMonth(Month atMonth) {
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
}
