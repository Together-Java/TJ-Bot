package org.togetherjava.tjbot.features.tophelper;

import org.jooq.Records;
import org.jooq.impl.DSL;

import org.togetherjava.tjbot.db.Database;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

// TODO Javadoc everywhere
public final class TopHelpersService {
    private static final int TOP_HELPER_LIMIT = 18;

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
        return database.read(context -> context
            .select(HELP_CHANNEL_MESSAGES.AUTHOR_ID, DSL.sum(HELP_CHANNEL_MESSAGES.MESSAGE_LENGTH))
            .from(HELP_CHANNEL_MESSAGES)
            .where(HELP_CHANNEL_MESSAGES.GUILD_ID.eq(guildId)
                .and(HELP_CHANNEL_MESSAGES.SENT_AT.between(start, end)))
            .groupBy(HELP_CHANNEL_MESSAGES.AUTHOR_ID)
            .orderBy(DSL.two().desc())
            .limit(TOP_HELPER_LIMIT)
            .fetch(Records.mapping(TopHelperResult::new)));
    }

    public record TopHelperResult(long authorId, BigDecimal messageLengths) {
    }

    public record TimeRange(Instant start, Instant end, String description) {
        public static TimeRange fromMonth(Month atMonth) {
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
