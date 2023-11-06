package org.togetherjava.tjbot.features.moderation.history;

import net.dv8tion.jda.api.JDA;
import org.jooq.impl.UpdatableRecordImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.MessageHistoryRecord;
import org.togetherjava.tjbot.features.Routine;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.togetherjava.tjbot.db.generated.Tables.MESSAGE_HISTORY;

/**
 * Routine that deletes records from message_history post expiration hours.
 */
public class MessageHistoryRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(MessageHistoryRoutine.class);
    private static final int SCHEDULE_INTERVAL_SECONDS = 30;
    private static final int EXPIRATION_HOURS = 2;
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database the database that contains records of messages to be purged.
     */
    public MessageHistoryRoutine(Database database) {
        this.database = database;
    }


    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, SCHEDULE_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    @Override
    public void runRoutine(JDA jda) {
        Instant now = Instant.now();
        Instant preExpirationHours = now.minus(EXPIRATION_HOURS, ChronoUnit.HOURS);

        Stream<MessageHistoryRecord> records =
                database.writeAndProvide(context -> context.selectFrom(MESSAGE_HISTORY)
                    .where(MESSAGE_HISTORY.SENT_AT.lessThan(preExpirationHours))
                    .stream());

        try (records) {
            records.forEach(UpdatableRecordImpl::delete);
        } catch (Exception exception) {
            logger.error(
                    "Unknown error happened during delete operation during message history routine",
                    exception);
        }
    }
}
