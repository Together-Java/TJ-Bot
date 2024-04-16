package org.togetherjava.tjbot.features.tophelper;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
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
import java.util.Arrays;
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
 * Command that displays the top helpers of a given time range.
 * <p>
 * Top helpers are measured by their message length in help channels, as set by
 * {@link TopHelpersMessageListener}.
 */
public final class TopHelpersCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersCommand.class);
    private static final String COMMAND_NAME = "top-helpers";
    private static final String MONTH_OPTION = "at-month";
    private static final int TOP_HELPER_LIMIT = 18;

    private static final int MAX_USER_NAME_LIMIT = 15;

    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database the database containing the message records of top helpers
     */
    public TopHelpersCommand(Database database) {
        super(COMMAND_NAME, "Lists top helpers for the last month, or a given month",
                CommandVisibility.GUILD);

        OptionData monthData = new OptionData(OptionType.STRING, MONTH_OPTION,
                "the month to compute for, by default the last month", false);
        Arrays.stream(Month.values())
            .forEach(month -> monthData.addChoice(
                    month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US), month.name()));
        getData().addOptions(monthData);

        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        OptionMapping atMonthData = event.getOption(MONTH_OPTION);

        TimeRange timeRange = computeTimeRange(computeMonth(atMonthData));
        List<TopHelperResult> topHelpers =
                computeTopHelpersDescending(event.getGuild().getIdLong(), timeRange);

        if (topHelpers.isEmpty()) {
            event
                .reply("No entries for the selected time range (%s)."
                    .formatted(timeRange.description()))
                .queue();
            return;
        }
        event.deferReply().queue();

        List<Long> topHelperIds = topHelpers.stream().map(TopHelperResult::authorId).toList();
        event.getGuild()
            .retrieveMembersByIds(topHelperIds)
            .onError(error -> handleError(error, event))
            .onSuccess(members -> handleTopHelpers(topHelpers, members, timeRange, event));
    }

    private static Month computeMonth(@Nullable OptionMapping atMonthData) {
        if (atMonthData != null) {
            return Month.valueOf(atMonthData.getAsString());
        }

        // Previous month
        return Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).getMonth();
    }

    private static TimeRange computeTimeRange(Month atMonth) {
        ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);

        int atYear = now.getYear();
        // E.g. using November, while it is March 2022, should use November 2021
        if (atMonth.compareTo(now.getMonth()) > 0) {
            atYear--;
        }
        YearMonth atYearMonth = YearMonth.of(atYear, atMonth);

        Instant start = atYearMonth.atDay(1).atTime(LocalTime.MIN).toInstant(ZoneOffset.UTC);
        Instant end = atYearMonth.atEndOfMonth().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        String description = "%s %d"
            .formatted(atMonth.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US), atYear);

        return new TimeRange(start, end, description);
    }

    private List<TopHelperResult> computeTopHelpersDescending(long guildId, TimeRange timeRange) {
        return database.read(context -> context
            .select(HELP_CHANNEL_MESSAGES.AUTHOR_ID, DSL.sum(HELP_CHANNEL_MESSAGES.MESSAGE_LENGTH))
            .from(HELP_CHANNEL_MESSAGES)
            .where(HELP_CHANNEL_MESSAGES.GUILD_ID.eq(guildId)
                .and(HELP_CHANNEL_MESSAGES.SENT_AT.between(timeRange.start(), timeRange.end())))
            .groupBy(HELP_CHANNEL_MESSAGES.AUTHOR_ID)
            .orderBy(DSL.two().desc())
            .limit(TOP_HELPER_LIMIT)
            .fetch(Records.mapping(TopHelperResult::new)));
    }

    private static void handleError(Throwable error, IDeferrableCallback event) {
        logger.warn("Failed to compute top-helpers", error);
        event.getHook().editOriginal("Sorry, something went wrong.").queue();
    }

    private static void handleTopHelpers(Collection<TopHelperResult> topHelpers,
            Collection<? extends Member> members, TimeRange timeRange, IDeferrableCallback event) {
        Map<Long, Member> userIdToMember =
                members.stream().collect(Collectors.toMap(Member::getIdLong, Function.identity()));

        List<List<String>> topHelpersDataTable = topHelpers.stream()
            .map(topHelper -> topHelperToDataRow(topHelper,
                    userIdToMember.get(topHelper.authorId())))
            .toList();

        String message = """
                ```java
                // for %s
                %s
                ```""".formatted(timeRange.description(), dataTableToString(topHelpersDataTable));

        event.getHook().editOriginal(message).queue();
    }

    private static List<String> topHelperToDataRow(TopHelperResult topHelper,
            @Nullable Member member) {
        String id = Long.toString(topHelper.authorId());
        String name = MessageUtils.abbreviate(
                member == null ? "UNKNOWN_USER" : member.getEffectiveName(), MAX_USER_NAME_LIMIT);
        String messageLengths = Long.toString(topHelper.messageLengths().longValue());

        return List.of(id, name, messageLengths);
    }

    private static String dataTableToString(Collection<List<String>> dataTable) {
        return dataTableToAsciiTable(dataTable,
                List.of(new ColumnSetting("Id", HorizontalAlign.RIGHT),
                        new ColumnSetting("Name", HorizontalAlign.RIGHT),
                        new ColumnSetting("Message lengths", HorizontalAlign.RIGHT)));
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

    private record TimeRange(Instant start, Instant end, String description) {
    }

    private record TopHelperResult(long authorId, BigDecimal messageLengths) {
    }

    private record ColumnSetting(String headerName, HorizontalAlign alignment) {
    }
}
