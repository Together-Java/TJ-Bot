package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.commands.EventReceiver;

/**
 * Initiates the bookmarks deletion period for a user when leaving the guild. When the user rejoins
 * the guild the deletion period will be canceled
 */
public final class LeftoverBookmarksListener extends ListenerAdapter implements EventReceiver {

    private final BookmarksSystem bookmarksSystem;

    /**
     * Creates a new instance.
     *
     * @param bookmarksSystem The {@link BookmarksSystem} to start or cancel the deletion period
     */
    public LeftoverBookmarksListener(BookmarksSystem bookmarksSystem) {
        this.bookmarksSystem = bookmarksSystem;
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        long userID = event.getUser().getIdLong();

        bookmarksSystem.startDeletionPeriodForUser(userID);
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        long userID = event.getUser().getIdLong();

        bookmarksSystem.cancelDeletionPeriodForUser(userID);
    }
}
