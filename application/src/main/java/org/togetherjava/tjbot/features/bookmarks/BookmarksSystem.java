package org.togetherjava.tjbot.features.bookmarks;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.BookmarksRecord;

import javax.annotation.Nullable;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.Bookmarks.BOOKMARKS;

/**
 * Maintains all bookmarks for all users and provides methods to create, query and remove them. Used
 * by the other bookmarks classes.
 */
public final class BookmarksSystem {

    static final int MAX_BOOKMARK_COUNT_TOTAL = 1_000_000;
    static final int WARN_BOOKMARK_COUNT_TOTAL = 900_000;
    static final int MAX_BOOKMARK_COUNT_PER_USER = 500;
    static final int MAX_NOTE_LENGTH = 150;
    private static final Duration REMOVE_BOOKMARKS_AFTER_LEAVE_DELAY = Duration.ofDays(7);

    static final Color COLOR_SUCCESS = new Color(166, 218, 149);
    static final Color COLOR_WARNING = new Color(245, 169, 127);
    static final Color COLOR_FAILURE = new Color(238, 153, 160);

    private final Database database;
    private final Predicate<String> isHelpForumName;

    /**
     * Creates a new instance of the bookmarks system.
     *
     * @param config The {@link Config} to get the overview channel pattern
     * @param database The {@link Database} to store and retrieve bookmarks
     */
    public BookmarksSystem(Config config, Database database) {
        this.database = database;

        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();
    }

    boolean isHelpThread(MessageChannelUnion channel) {
        if (channel.getType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return false;
        }

        ThreadChannel threadChannel = channel.asThreadChannel();
        String parentChannelName = threadChannel.getParentChannel().getName();

        return isHelpForumName.test(parentChannelName);
    }

    boolean didUserBookmarkChannel(long userID, long channelID) {
        return database.read(context -> context.selectFrom(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(userID), BOOKMARKS.CHANNEL_ID.eq(channelID))
            .limit(1)
            .fetchOne() != null);
    }

    void addBookmark(long authorID, long channelID, @Nullable String note) {
        database.write(context -> context.newRecord(BOOKMARKS)
            .setAuthorId(authorID)
            .setChannelId(channelID)
            .setCreatedAt(Instant.now())
            .setNote(note)
            .setDeleteAt(null)
            .insert());
    }

    List<BookmarksRecord> getUsersBookmarks(long authorID) {
        return database.read(context -> context.selectFrom(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .orderBy(BOOKMARKS.CREATED_AT.desc())
            .fetch());
    }

    void removeBookmarks(long authorID, Set<Long> channelIDs) {
        database.write(context -> context.deleteFrom(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID), BOOKMARKS.CHANNEL_ID.in(channelIDs))
            .execute());
    }

    int getTotalBookmarkCount() {
        return database.read(context -> context.fetchCount(BOOKMARKS));
    }

    int getUserBookmarkCount(long authorID) {
        return database
            .read(context -> context.fetchCount(BOOKMARKS, BOOKMARKS.AUTHOR_ID.eq(authorID)));
    }

    void startDeletionPeriodForUser(long authorID) {
        Instant deleteAt = Instant.now().plus(REMOVE_BOOKMARKS_AFTER_LEAVE_DELAY);

        database.write(context -> context.update(BOOKMARKS)
            .set(BOOKMARKS.DELETE_AT, deleteAt)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .execute());
    }

    void cancelDeletionPeriodForUser(long authorID) {
        database.write(context -> context.update(BOOKMARKS)
            .setNull(BOOKMARKS.DELETE_AT)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .execute());
    }

    void deleteLeftoverBookmarks() {
        database.write(context -> context.deleteFrom(BOOKMARKS)
            .where(BOOKMARKS.DELETE_AT.isNotNull(), BOOKMARKS.DELETE_AT.lessThan(Instant.now()))
            .execute());
    }

    private static MessageEmbed createColoredEmbed(String content, Color color) {
        return new EmbedBuilder().setDescription(content).setColor(color).build();
    }

    static MessageEmbed createSuccessEmbed(String content) {
        return createColoredEmbed(content, COLOR_SUCCESS);
    }

    static MessageEmbed createWarningEmbed(String content) {
        return createColoredEmbed(content, COLOR_WARNING);
    }

    static MessageEmbed createFailureEmbed(String content) {
        return createColoredEmbed(content, COLOR_FAILURE);
    }

}
