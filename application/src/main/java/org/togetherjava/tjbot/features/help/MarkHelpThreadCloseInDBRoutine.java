package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.HelpThreadsRecord;
import org.togetherjava.tjbot.features.Routine;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.db.generated.tables.HelpThreads.HELP_THREADS;

/**
 * Updates the status of help threads in database that were created a few days ago and couldn't be
 * closed.
 */
public final class MarkHelpThreadCloseInDBRoutine implements Routine {
    private final Logger logger = LoggerFactory.getLogger(MarkHelpThreadCloseInDBRoutine.class);
    private final Database database;
    private final HelpThreadLifecycleListener helpThreadLifecycleListener;

    /**
     * Creates a new instance.
     *
     * @param database the database to store help thread metadata in
     * @param helpThreadLifecycleListener class which offers method to update thread status in
     *        database
     */
    public MarkHelpThreadCloseInDBRoutine(Database database,
            HelpThreadLifecycleListener helpThreadLifecycleListener) {
        this.database = database;
        this.helpThreadLifecycleListener = helpThreadLifecycleListener;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        updateTicketStatus(jda);
    }

    private void updateTicketStatus(JDA jda) {
        Instant now = Instant.now();
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        List<Long> threadIdsToClose = database.read(context -> context.selectFrom(HELP_THREADS)
            .where(HELP_THREADS.TICKET_STATUS.eq(HelpSystemHelper.TicketStatus.ACTIVE.val))
            .and(HELP_THREADS.CREATED_AT.lessThan(threeDaysAgo))
            .stream()
            .map(HelpThreadsRecord::getChannelId)
            .toList());


        threadIdsToClose.forEach(id -> {
            try {
                ThreadChannel threadChannel = jda.getThreadChannelById(id);
                helpThreadLifecycleListener.handleArchiveStatus(now, threadChannel);
            } catch (Exception exception) {
                logger.warn("unable to mark thread as close with id :{}", id, exception);
            }
        });
    }
}
