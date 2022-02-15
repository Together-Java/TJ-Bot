package org.togetherjava.tjbot.commands.tophelper;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Command that displays the top helpers of a given time range.
 * <p>
 * Top helpers are measured by their message count in help channels, as set by
 * {@link TopHelpersMessageListener}.
 */
public final class TopHelpersCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersCommand.class);
    private static final String COMMAND_NAME = "top-helpers";
    private static final int TOP_HELPER_LIMIT = 20;

    private final Database database;
    private final Predicate<String> hasRequiredRole;

    /**
     * Creates a new instance.
     *
     * @param database the database containing the message counts of top helpers
     */
    public TopHelpersCommand(@NotNull Database database) {
        super(COMMAND_NAME, "Lists top helpers for the last month", CommandVisibility.GUILD);
        // TODO Add options to optionally pick a time range once JDA/Discord offers a date-picker
        hasRequiredRole = Pattern.compile(Config.getInstance().getSoftModerationRolePattern())
            .asMatchPredicate();
        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        if (!handleHasAuthorRole(event.getMember(), event)) {
            return;
        }

        TimeRange timeRange = computeDefaultTimeRange();
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

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean handleHasAuthorRole(@NotNull Member author, @NotNull IReplyCallback event) {
        if (author.getRoles().stream().map(Role::getName).anyMatch(hasRequiredRole)) {
            return true;
        }
        event.reply("You can not compute the top-helpers since you do not have the required role.")
            .setEphemeral(true)
            .queue();
        return false;
    }

    private static @NotNull TimeRange computeDefaultTimeRange() {
        // Last month
        ZonedDateTime start = Instant.now()
            .atZone(ZoneOffset.UTC)
            .minusMonths(1)
            .with(TemporalAdjusters.firstDayOfMonth());
        ZonedDateTime end = start.with(TemporalAdjusters.lastDayOfMonth());
        String description = start.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.US);

        return new TimeRange(start.toInstant(), end.toInstant(), description);
    }

    private @NotNull List<TopHelperResult> computeTopHelpersDescending(long guildId,
            @NotNull TimeRange timeRange) {
        return database.read(context -> context.select(HELP_CHANNEL_MESSAGES.AUTHOR_ID, DSL.count())
            .from(HELP_CHANNEL_MESSAGES)
            .where(HELP_CHANNEL_MESSAGES.GUILD_ID.eq(guildId)
                .and(HELP_CHANNEL_MESSAGES.SENT_AT.between(timeRange.start(), timeRange.end())))
            .groupBy(HELP_CHANNEL_MESSAGES.AUTHOR_ID)
            .orderBy(DSL.count().desc())
            .limit(TOP_HELPER_LIMIT)
            .fetch(Records.mapping(TopHelperResult::new)));
    }

    private static void handleError(@NotNull Throwable error, @NotNull IDeferrableCallback event) {
        logger.warn("Failed to compute top-helpers", error);
        event.getHook().editOriginal("Sorry, something went wrong.").queue();
    }

    private static void handleTopHelpers(@NotNull Collection<TopHelperResult> topHelpers,
            @NotNull Collection<? extends Member> members, @NotNull TimeRange timeRange,
            @NotNull IDeferrableCallback event) {
        Map<Long, Member> userIdToMember =
                members.stream().collect(Collectors.toMap(Member::getIdLong, Function.identity()));

        List<List<String>> topHelpersDataTable = topHelpers.stream()
            .map(topHelper -> topHelperToDataRow(topHelper,
                    userIdToMember.get(topHelper.authorId())))
            .toList();

        String message =
                "```java%n%s%n```".formatted(dataTableToString(topHelpersDataTable, timeRange));

        event.getHook().editOriginal(message).queue();
    }

    private static @NotNull List<String> topHelperToDataRow(@NotNull TopHelperResult topHelper,
            @Nullable Member member) {
        String id = Long.toString(topHelper.authorId());
        String name = member == null ? "UNKNOWN_USER" : member.getEffectiveName();
        String messageCount = Integer.toString(topHelper.messageCount());

        return List.of(id, name, messageCount);
    }

    private static @NotNull String dataTableToString(@NotNull Collection<List<String>> dataTable,
            @NotNull TimeRange timeRange) {
        return dataTableToAsciiTable(dataTable,
                List.of(new ColumnSetting("Id", HorizontalAlign.RIGHT),
                        new ColumnSetting("Name", HorizontalAlign.RIGHT),
                        new ColumnSetting(
                                "Message count (for %s)".formatted(timeRange.description()),
                                HorizontalAlign.RIGHT)));
    }

    private static @NotNull String dataTableToAsciiTable(
            @NotNull Collection<List<String>> dataTable,
            @NotNull List<ColumnSetting> columnSettings) {
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

    private record TopHelperResult(long authorId, int messageCount) {
    }

    private record ColumnSetting(String headerName, HorizontalAlign alignment) {
    }
}
