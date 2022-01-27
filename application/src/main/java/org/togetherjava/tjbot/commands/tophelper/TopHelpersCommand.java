package org.togetherjava.tjbot.commands.tophelper;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;

import java.time.Instant;
import java.time.Period;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Command that displays the top helpers of a given time range.
 *
 * Top helpers are measured by their message count in help channels, as set by
 * {@link TopHelpersMessageListener}.
 */
public final class TopHelpersCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersCommand.class);
    private static final String COMMAND_NAME = "top-helpers";
    private static final int TOP_HELPER_LIMIT = 20;

    private final Database database;

    /**
     * Creates a new instance.
     * 
     * @param database the database containing the message counts of top helpers
     */
    public TopHelpersCommand(@NotNull Database database) {
        super(COMMAND_NAME, "Lists top helpers for the last 30 days", SlashCommandVisibility.GUILD);
        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        List<TopHelperResult> topHelpers =
                computeTopHelpersDescending(event.getGuild().getIdLong());

        if (topHelpers.isEmpty()) {
            event.reply("No entries for the selected time range.").queue();
        }
        event.deferReply().queue();

        List<Long> topHelperIds = topHelpers.stream().map(TopHelperResult::authorId).toList();
        event.getGuild()
            .retrieveMembersByIds(topHelperIds)
            .onError(error -> handleError(error, event))
            .onSuccess(members -> handleTopHelpers(topHelpers, members, event));
    }

    private @NotNull List<TopHelperResult> computeTopHelpersDescending(long guildId) {
        return database.read(context -> context.select(HELP_CHANNEL_MESSAGES.AUTHOR_ID, DSL.count())
            .from(HELP_CHANNEL_MESSAGES)
            .where(HELP_CHANNEL_MESSAGES.GUILD_ID.eq(guildId)
                .and(HELP_CHANNEL_MESSAGES.SENT_AT
                    .greaterOrEqual(Instant.now().minus(Period.ofDays(30)))))
            .groupBy(HELP_CHANNEL_MESSAGES.AUTHOR_ID)
            .orderBy(DSL.count().desc())
            .limit(TOP_HELPER_LIMIT)
            .fetch(Records.mapping(TopHelperResult::new)));
    }

    private static void handleError(@NotNull Throwable error, @NotNull Interaction event) {
        logger.warn("Failed to compute top-helpers", error);
        event.getHook().editOriginal("Sorry, something went wrong.").queue();
    }

    private static void handleTopHelpers(@NotNull Collection<TopHelperResult> topHelpers,
            @NotNull Collection<? extends Member> members, @NotNull Interaction event) {
        Map<Long, Member> userIdToMember =
                members.stream().collect(Collectors.toMap(Member::getIdLong, Function.identity()));

        List<List<String>> topHelpersDataTable = topHelpers.stream()
            .map(topHelper -> topHelperToDataRow(topHelper,
                    userIdToMember.get(topHelper.authorId())))
            .toList();

        String message = "```java%n%s%n```".formatted(dataTableToString(topHelpersDataTable));

        event.getHook().editOriginal(message).queue();
    }

    private static @NotNull List<String> topHelperToDataRow(@NotNull TopHelperResult topHelper,
            @Nullable Member member) {
        String id = Long.toString(topHelper.authorId());
        String name = member == null ? "UNKNOWN_USER" : member.getEffectiveName();
        String messageCount = Integer.toString(topHelper.messageCount());

        return List.of(id, name, messageCount);
    }

    private static @NotNull String dataTableToString(@NotNull Collection<List<String>> dataTable) {
        return dataTableToAsciiTable(dataTable,
                List.of(new ColumnSetting("Id", HorizontalAlign.RIGHT),
                        new ColumnSetting("Name", HorizontalAlign.RIGHT),
                        new ColumnSetting("Message count (30 days)", HorizontalAlign.RIGHT)));
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

    private record TopHelperResult(long authorId, int messageCount) {
    }

    private record ColumnSetting(String headerName, HorizontalAlign alignment) {
    }
}
