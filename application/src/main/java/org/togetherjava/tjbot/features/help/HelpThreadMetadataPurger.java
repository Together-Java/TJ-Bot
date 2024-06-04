package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpThreads;
import org.togetherjava.tjbot.features.Routine;

import java.time.Instant;
import java.time.Period;
import java.util.concurrent.TimeUnit;

/**
 * Purge Routine to get rid of old thread creations in the database.
 */
public class HelpThreadMetadataPurger implements Routine {
    private final Database database;
    private static final Logger logger = LoggerFactory.getLogger(HelpThreadMetadataPurger.class);
    private static final Period DELETE_MESSAGE_RECORDS_AFTER = Period.ofDays(180);

    /**
     * Creates a new instance.
     *
     * @param database the database used to purge help thread metadata
     */
    public HelpThreadMetadataPurger(Database database) {
        this.database = database;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.DAYS);
    }

    @Override
    public void runRoutine(JDA jda) {
        int recordsDeleted =
                database.writeAndProvide(content -> content.deleteFrom(HelpThreads.HELP_THREADS))
                    .where(HelpThreads.HELP_THREADS.CREATED_AT
                        .lessOrEqual(Instant.now().minus(DELETE_MESSAGE_RECORDS_AFTER)))
                    .execute();
        if (recordsDeleted > 0) {
            logger.debug("{} old thread channels deleted because they are older than {}.",
                    recordsDeleted, DELETE_MESSAGE_RECORDS_AFTER);
        }
    }
}
