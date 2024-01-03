package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.JDA;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.Routine;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.db.generated.tables.HelpThreads.HELP_THREADS;

/**
 * Updates the status of help threads in database that were created a few days ago and couldn't be
 * closed.
 */
public final class MarkHelpThreadCloseInDBRoutine implements Routine {
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database the database to store help thread metadata in
     */
    public MarkHelpThreadCloseInDBRoutine(Database database) {
        this.database = database;
    }


    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        updateTicketStatus();
    }

    private void updateTicketStatus() {
        Instant now = Instant.now();
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        database.write(context -> context.update(HELP_THREADS)
            .set(HELP_THREADS.TICKET_STATUS, HelpSystemHelper.TicketStatus.ARCHIVED.val)
            .where(HELP_THREADS.CREATED_AT.lessOrEqual(threeDaysAgo)
                .and(HELP_THREADS.TICKET_STATUS.eq(HelpSystemHelper.TicketStatus.ACTIVE.val)))
            .execute());
    }
}
