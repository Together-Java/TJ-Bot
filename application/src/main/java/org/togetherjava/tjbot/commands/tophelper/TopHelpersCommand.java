package org.togetherjava.tjbot.commands.tophelper;

import com.github.freva.asciitable.HorizontalAlign;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import org.jetbrains.annotations.NotNull;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command to retrieve top helpers for last 30 days.
 */
public final class TopHelpersCommand extends SlashCommandAdapter {
    private static final String COMMAND_NAME = "top-helpers";

    public static final String PLAINTEXT_MESSAGE_TEMPLATE = "```\n%s\n```";
    private static final String COUNT_OPTION = "count";
    private static final String NO_ENTRIES = "No entries";

    private static final int HELPER_LIMIT = 30;

    private record TopHelperRow(Integer serialId, Long userId, Long messageCount) {
    }

    private final Database database;

    /**
     * Initializes TopHelpers with a database.
     *
     * @param database the database to store the key-value pairs in
     */
    public TopHelpersCommand(@NotNull Database database) {
        super(COMMAND_NAME, "Lists top helpers for the last 30 days", SlashCommandVisibility.GUILD);
        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        long guildId = event.getGuild().getIdLong();
        database.readAndConsume(context -> {
            List<TopHelperRow> records = context.with("TOPHELPERS")
                .as(DSL
                    .select(HelpChannelMessages.HELP_CHANNEL_MESSAGES.AUTHOR_ID,
                            DSL.count().as("COUNT"))
                    .from(HelpChannelMessages.HELP_CHANNEL_MESSAGES)
                    .where(HelpChannelMessages.HELP_CHANNEL_MESSAGES.GUILD_ID.eq(guildId))
                    .groupBy(HelpChannelMessages.HELP_CHANNEL_MESSAGES.AUTHOR_ID)
                    .orderBy(DSL.count().desc())
                    .limit(HELPER_LIMIT))
                .select(DSL.rowNumber()
                    .over(DSL.orderBy(DSL.field(DSL.name("COUNT")).desc()))
                    .as("#"), DSL.field(DSL.name("AUTHOR_ID"), Long.class),
                        DSL.field(DSL.name("COUNT"), Long.class))
                .from(DSL.table(DSL.name("TOPHELPERS")))
                .fetch(Records.mapping(TopHelperRow::new));
            generateResponse(event, records);
        });
    }

    private static @NotNull String prettyFormatOutput(@NotNull List<List<String>> dataFrame) {
        return String.format(PLAINTEXT_MESSAGE_TEMPLATE,
                dataFrame.isEmpty() ? NO_ENTRIES
                        : PresentationUtils.dataFrameToAsciiTable(dataFrame,
                                new String[] {"#", "Name", "Message Count (in the last 30 days)"},
                                new HorizontalAlign[] {HorizontalAlign.RIGHT, HorizontalAlign.LEFT,
                                        HorizontalAlign.RIGHT}));
    }

    private static void generateResponse(@NotNull Interaction event,
            @NotNull Collection<TopHelperRow> records) {
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
