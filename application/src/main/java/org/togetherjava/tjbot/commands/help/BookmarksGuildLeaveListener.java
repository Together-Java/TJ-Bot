package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.commands.EventReceiver;

/**
 * Schedules a users bookmarks to be removed when leaving the guild. Also cancels the scheduled
 * removal when rejoining before the bookmarks were removed.
 */
public final class BookmarksGuildLeaveListener extends ListenerAdapter implements EventReceiver {

    private final BookmarksSystem bookmarksSystem;

    /**
     * Creates a new instance.
     *
     * @param bookmarksSystem The bookmarks system to use
     */
    public BookmarksGuildLeaveListener(BookmarksSystem bookmarksSystem) {
        this.bookmarksSystem = bookmarksSystem;
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        long userID = event.getUser().getIdLong();

        bookmarksSystem.scheduleUsersBookmarksRemoval(userID);
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        long userID = event.getUser().getIdLong();

        bookmarksSystem.cancelUsersBookmarksRemoval(userID);
    }
}
