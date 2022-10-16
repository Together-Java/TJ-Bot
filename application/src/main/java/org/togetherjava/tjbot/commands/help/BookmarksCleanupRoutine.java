package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;

import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.db.Database;

import java.util.concurrent.TimeUnit;

/**
 * This routine cleans up expired bookmarks and removed old paginators, even if the user doesnt run
 * the bookmarks command. This is done to save disk space
 */
public class BookmarksCleanupRoutine implements Routine {

    private final Database database;

    public BookmarksCleanupRoutine(Database database) {
        this.database = database;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 1, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        BookmarksHelper.cleanupBookmarks(database);
        BookmarksHelper.cleanupPaginators();
    }

}
