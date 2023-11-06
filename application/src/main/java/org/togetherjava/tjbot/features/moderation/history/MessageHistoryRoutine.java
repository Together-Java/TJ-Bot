package org.togetherjava.tjbot.features.moderation.history;

import net.dv8tion.jda.api.JDA;
import org.jooq.impl.UpdatableRecordImpl;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.Routine;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.db.generated.Tables.MESSAGE_HISTORY;

/**
 * Routine that deletes records from message_history post expiration hours.
 */
public class MessageHistoryRoutine implements Routine {

    private static final int SCHEDULE_INTERVAL_SECONDS = 30;
    private static final int EXPIRATION_HOURS = 2;
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database the database that records some metadata on each message received.
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

        database.write(context -> context.selectFrom(MESSAGE_HISTORY)
            .where(MESSAGE_HISTORY.SENT_AT.lessThan(preExpirationHours))
            .stream()
            .forEach(UpdatableRecordImpl::delete));
    }
}
