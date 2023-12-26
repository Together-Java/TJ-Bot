package org.togetherjava.tjbot.features.tophelper;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.Routine;

import java.time.Instant;
import java.time.Period;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Cleanup routine to get rid of old database top-helper message entries.
 */
public final class TopHelpersPurgeMessagesRoutine implements Routine {
    private static final Logger logger =
            LoggerFactory.getLogger(TopHelpersPurgeMessagesRoutine.class);
    private static final Period DELETE_MESSAGE_RECORDS_AFTER = Period.ofDays(90);

    private final Database database;

    /**
     * Creates a new cleanup routine.
     *
     * @param database the database that contains the messages to purge
     */
    public TopHelpersPurgeMessagesRoutine(Database database) {
        this.database = database;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 4, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        int recordsDeleted =
                database.writeAndProvide(context -> context.deleteFrom(HELP_CHANNEL_MESSAGES)
                    .where(HELP_CHANNEL_MESSAGES.SENT_AT
                        .lessOrEqual(Instant.now().minus(DELETE_MESSAGE_RECORDS_AFTER)))
                    .execute());

        if (recordsDeleted > 0) {
            logger.debug(
                    "{} old help message records have been deleted because they are older than {}.",
                    recordsDeleted, DELETE_MESSAGE_RECORDS_AFTER);
        }
    }
}
