package org.togetherjava.tjbot.commands;

import com.github.freva.asciitable.HorizontalAlign;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.Records;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.util.PresentationUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;
import static org.togetherjava.tjbot.db.generated.tables.MessageMetadata.MESSAGE_METADATA;
import static org.togetherjava.tjbot.util.MessageTemplate.PLAINTEXT_MESSAGE_TEMPLATE;

public final class TopHelpers extends SlashCommandAdapter {

    private static final String COUNT_OPTION = "count";
    private static final String NO_ENTRIES = "No entries";

    private static final long MIN_COUNT = 10L;
    private static final long MAX_COUNT = 30L;

    private record TopHelperRow(Integer serialId, Long userId, Long messageCount) {
    }

    private final Database database;

    /**
     * Initializes TopHelpers with a database.
     *
     * @param database the database to store the key-value pairs in
     */
    public TopHelpers(Database database) {
        super("tophelpers", "Find top helpers for last 30 days", SlashCommandVisibility.GUILD);
        this.database = database;
        getData().addOptions(new OptionData(OptionType.INTEGER, COUNT_OPTION,
                "Count of top helpers to be retrieved (default is 10 and capped at 30)", false));
    }

    @SuppressWarnings("squid:S1192")
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        Long guildId = event.getGuild().getIdLong();
        Long count = getBoundedCountAsLong(event.getOption(COUNT_OPTION));
        database.read(dsl -> {
            List<TopHelperRow> records = dsl.with("TOPHELPERS")
                .as(select(MESSAGE_METADATA.USER_ID, count().as("COUNT")).from(MESSAGE_METADATA)
                    .where(MESSAGE_METADATA.GUILD_ID.eq(guildId))
                    .groupBy(MESSAGE_METADATA.USER_ID)
                    .orderBy(count().desc())
                    .limit(count))
                .select(rowNumber().over(orderBy(field(name("COUNT")).desc())).as("#"),
                        field(name("USER_ID"), Long.class), field(name("COUNT"), Long.class))
                .from(table(name("TOPHELPERS")))
                .fetch(Records.mapping(TopHelperRow::new));
            generateResponse(event, records);
        });
    }

    private static long getBoundedCountAsLong(@Nullable OptionMapping count) {
        return count == null ? MIN_COUNT : Math.min(count.getAsLong(), MAX_COUNT);
    }

    private static String prettyFormatOutput(@NotNull List<List<String>> dataFrame) {
        return String.format(PLAINTEXT_MESSAGE_TEMPLATE,
                dataFrame.isEmpty() ? NO_ENTRIES
                        : PresentationUtils.dataFrameToAsciiTable(dataFrame,
                                new String[] {"#", "Name", "Message Count (in the last 30 days)"},
                                new HorizontalAlign[] {HorizontalAlign.RIGHT, HorizontalAlign.LEFT,
                                        HorizontalAlign.RIGHT}));
    }

    private static void generateResponse(@NotNull SlashCommandEvent event,
            @NotNull List<TopHelperRow> records) {
        List<Long> userIds = records.stream().map(TopHelperRow::userId).toList();
        event.getGuild().retrieveMembersByIds(userIds).onSuccess(members -> {
            Map<Long, String> activeUserIdToEffectiveNames = members.stream()
                .collect(Collectors.toMap(Member::getIdLong, Member::getEffectiveName));
            List<List<String>> topHelpersDataframe = records.stream()
                .map(topHelperRow -> List.of(topHelperRow.serialId.toString(),
                        activeUserIdToEffectiveNames.getOrDefault(topHelperRow.userId,
                                // Any user who is no more a part of the guild is marked as
                                // [UNKNOWN]
                                "[UNKNOWN]"),
                        topHelperRow.messageCount.toString()))
                .toList();
            event.reply(prettyFormatOutput(topHelpersDataframe)).queue();
        });
    }
}
