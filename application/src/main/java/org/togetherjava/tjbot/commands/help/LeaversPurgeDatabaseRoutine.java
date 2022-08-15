package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.AddHelpChannel;

import java.time.Instant;
import java.time.Period;
import java.util.concurrent.TimeUnit;

public class LeaversPurgeDatabaseRoutine implements Routine {
    private final Database database;
    private static final Logger logger = LoggerFactory.getLogger(LeaversPurgeDatabaseRoutine.class);
    private static final Period DELETE_MESSAGE_RECORDS_AFTER = Period.ofDays(7);

    public LeaversPurgeDatabaseRoutine(@NotNull Database database) {
        this.database = database;
    }


    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 4, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        int recordsDeleted = database
            .writeAndProvide(content -> content.deleteFrom(AddHelpChannel.ADD_HELP_CHANNEL))
            .where(AddHelpChannel.ADD_HELP_CHANNEL.CREATED_AT
                .lessOrEqual(Instant.now().minus(DELETE_MESSAGE_RECORDS_AFTER)))
            .execute();
        if (recordsDeleted > 0) {
            logger.debug("{} old thread channels deleted because they are older than {}.",
                    recordsDeleted, DELETE_MESSAGE_RECORDS_AFTER);
        }

    }
}
