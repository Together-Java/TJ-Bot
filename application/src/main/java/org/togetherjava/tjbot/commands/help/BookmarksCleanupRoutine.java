package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.JDA;

import org.togetherjava.tjbot.commands.Routine;

import java.util.concurrent.TimeUnit;

/**
 * Tells the bookmarks system to clean bookmarks scheduled for removal
 */
public final class BookmarksCleanupRoutine implements Routine {

    private final BookmarksSystem bookmarksSystem;

    /**
     * Creates a new instance.
     *
     * @param bookmarksSystem The bookmarks system to use
     */
    public BookmarksCleanupRoutine(BookmarksSystem bookmarksSystem) {
        this.bookmarksSystem = bookmarksSystem;
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 6, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        bookmarksSystem.cleanRemovalScheduledBookmarks();
    }

}
