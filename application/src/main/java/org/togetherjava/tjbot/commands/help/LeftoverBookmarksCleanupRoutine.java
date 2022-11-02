package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;

import org.togetherjava.tjbot.commands.Routine;

import java.util.concurrent.TimeUnit;

/**
 * Tells the bookmarks system to delete the old bookmarks of users that left the guild
 */
public final class LeftoverBookmarksCleanupRoutine implements Routine {

    private final BookmarksSystem bookmarksSystem;

    /**
     * Creates a new instance.
     *
     * @param bookmarksSystem The bookmarks system to use
     */
    public LeftoverBookmarksCleanupRoutine(BookmarksSystem bookmarksSystem) {
        this.bookmarksSystem = bookmarksSystem;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 6, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        bookmarksSystem.deleteLeftoverBookmarks();
    }

}
