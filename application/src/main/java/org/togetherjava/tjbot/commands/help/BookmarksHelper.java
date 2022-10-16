package org.togetherjava.tjbot.commands.help;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.BookmarksRecord;
import org.togetherjava.tjbot.db.generated.tables.records.HelpThreadsRecord;

import javax.annotation.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.togetherjava.tjbot.db.generated.Tables.BOOKMARKS;
import static org.togetherjava.tjbot.db.generated.Tables.HELP_THREADS;

public class BookmarksHelper {

    private BookmarksHelper() {
        throw new IllegalStateException("Utility class");
    }

    protected static Optional<HelpThreadsRecord> getHelpThread(Database database, long channelId) {
        return database.read(context -> context.selectFrom(HELP_THREADS)
            .where(HELP_THREADS.CHANNEL_ID.eq(channelId))
            .fetch()
            .stream()
            .findFirst());
    }

    protected static Optional<BookmarksRecord> getBookmark(Database database, long creatorId,
            long channelId) {
        return database.read(context -> context.selectFrom(BOOKMARKS)
            .where(BOOKMARKS.CREATOR_ID.eq(creatorId), BOOKMARKS.CHANNEL_ID.eq(channelId))
            .stream()
            .findFirst());
    }

    protected static void addBookmark(Database database, long creatorId, long channelId,
            String originalTitle, @Nullable String note) {
        database.write(context -> context.newRecord(BOOKMARKS)
            .setCreatorId(creatorId)
            .setChannelId(channelId)
            .setOriginalTitle(originalTitle)
            .setLastRenewedAt(Instant.now())
            .setNote(note)
            .insert());
    }

    protected static void renewBookmark(Database database, long creatorId, long channelId) {
        database.write(context -> context.update(BOOKMARKS)
            .set(BOOKMARKS.LAST_RENEWED_AT, Instant.now())
            .where(BOOKMARKS.CREATOR_ID.eq(creatorId), BOOKMARKS.CHANNEL_ID.eq(channelId))
            .execute());
    }

    protected static void removeBookmark(Database database, long creatorId, long channelId) {
        database.write(context -> context.deleteFrom(BOOKMARKS)
            .where(BOOKMARKS.CREATOR_ID.eq(creatorId), BOOKMARKS.CHANNEL_ID.eq(channelId))
            .execute());
    }

    protected static List<BookmarksRecord> getUserBookmarks(Database database, long creatorId) {
        return database.read(context -> context.selectFrom(BOOKMARKS)
            .where(BOOKMARKS.CREATOR_ID.eq(creatorId))
            .stream()
            .toList());
    }

    protected static void cleanupUserBookmarks(Database database, long creatorId) {
        database.write(context -> context.deleteFrom(BOOKMARKS)
            .where(BOOKMARKS.CREATOR_ID.eq(creatorId),
                    BOOKMARKS.LAST_RENEWED_AT.lessThan(Instant.now()
                        .minus(BookmarksCommand.EXPIRE_DELAY_SECONDS, ChronoUnit.SECONDS)))
            .execute());
    }

    protected static void cleanupBookmarks(Database database) {
        database.write(context -> context.deleteFrom(BOOKMARKS)
            .where(BOOKMARKS.LAST_RENEWED_AT.lessThan(
                    Instant.now().minus(BookmarksCommand.EXPIRE_DELAY_SECONDS, ChronoUnit.SECONDS)))
            .execute());
    }

    protected static void cleanupPaginators() {
        Map<String, BookmarksPaginator> paginators = BookmarksCommand.getBookmarksPaginators();
        new HashMap<>(paginators).forEach((uuid, paginator) -> {
            if (paginator.getLastUpdated()
                .isBefore(Instant.now()
                    .minus(BookmarksPaginator.EXPIRE_DELAY_SECONDS, ChronoUnit.SECONDS)))
                paginators.remove(uuid);
        });
    }

}
