package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.BookmarksRecord;

import javax.annotation.Nullable;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.Bookmarks.BOOKMARKS;

/**
 * The bookmarks system provides methods to interact with the database. It also enables the
 * {@link BookmarksCommand} to request paginations from the {@link BookmarksPaginatorInteractor}.
 */
public final class BookmarksSystem {

    public static final Color COLOR_SUCCESS = new Color(166, 218, 149);
    public static final Color COLOR_WARNING = new Color(245, 169, 127);
    public static final Color COLOR_FAILURE = new Color(238, 153, 160);

    public static final int MAX_BOOKMARK_COUNT_TOTAL = 1_000_000;
    public static final int WARN_BOOKMARK_COUNT_TOTAL = 900_000;
    public static final int MAX_BOOKMARK_COUNT_USER = 500;
    public static final int MAX_NOTE_LENGTH = 150;
    public static final Duration LEAVE_REMOVAL_DELAY = Duration.ofDays(7);

    private final Database database;
    private final Predicate<String> isOverviewChannelName;

    private Consumer<GenericCommandInteractionEvent> listPaginationConsumer = null;
    private Consumer<GenericCommandInteractionEvent> removePaginationConsumer = null;

    /**
     * Creates a new instance of the bookmarks system.
     *
     * @param config The config to use
     * @param database The database to use
     */
    public BookmarksSystem(Config config, Database database) {
        this.database = database;

        isOverviewChannelName = Pattern.compile(config.getHelpSystem().getOverviewChannelPattern())
            .asMatchPredicate();
    }

    /**
     * Accepts the consumers for used for requesting the list and remove paginations.
     *
     * @param listPaginationConsumer The list pagination handler
     * @param removePaginationConsumer The remove pagination handler
     */
    void acceptPaginationConsumers(Consumer<GenericCommandInteractionEvent> listPaginationConsumer,
            Consumer<GenericCommandInteractionEvent> removePaginationConsumer) {
        this.listPaginationConsumer = listPaginationConsumer;
        this.removePaginationConsumer = removePaginationConsumer;
    }

    /**
     * Requests a list pagination from the {@link BookmarksPaginatorInteractor}.
     *
     * @param event The command interaction event
     */
    void requestListPagination(GenericCommandInteractionEvent event) {
        if (listPaginationConsumer == null) {
            throw new AssertionError("No list pagination consumer was provided");
        }

        listPaginationConsumer.accept(event);
    }

    /**
     * Requests a remove pagination from the {@link BookmarksPaginatorInteractor}.
     *
     * @param event The command interaction event
     */
    void requestRemovePagination(GenericCommandInteractionEvent event) {
        if (removePaginationConsumer == null) {
            throw new AssertionError("No remove pagination consumer was provided");
        }

        removePaginationConsumer.accept(event);
    }

    boolean isHelpThread(MessageChannelUnion channel) {
        if (channel.getType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return false;
        }

        ThreadChannel threadChannel = channel.asThreadChannel();
        String parentChannelName = threadChannel.getParentMessageChannel().getName();

        return isOverviewChannelName.test(parentChannelName);
    }

    boolean didUserBookmarkChannel(long userID, long channelID) {
        return database.read(context -> context.selectFrom(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(userID), BOOKMARKS.CHANNEL_ID.eq(channelID))
            .fetch()
            .isNotEmpty());
    }

    void addBookmark(long authorID, long channelID, @Nullable String note) {
        database.write(context -> context.newRecord(BOOKMARKS)
            .setAuthorId(authorID)
            .setChannelId(channelID)
            .setCreatedAt(Instant.now())
            .setNote(note)
            .setScheduledRemovalAt(null)
            .insert());
    }

    List<BookmarksRecord> getUsersBookmarks(long authorID) {
        return database.read(context -> context.selectFrom(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .orderBy(BOOKMARKS.CREATED_AT.desc())
            .fetch());
    }

    void removeBookmarks(long authorID, List<Long> channelIDs) {
        database.write(context -> context.deleteFrom(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID), BOOKMARKS.CHANNEL_ID.in(channelIDs))
            .execute());
    }

    int getTotalBookmarkCount() {
        return database
            .read(context -> context.selectCount().from(BOOKMARKS).fetchOne(0, int.class));
    }

    int getUserBookmarkCount(long authorID) {
        return database.read(context -> context.selectCount()
            .from(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .fetchOne(0, int.class));
    }

    void scheduleUsersBookmarksRemoval(long authorID) {
        Instant scheduledRemovalAt = Instant.now().plus(LEAVE_REMOVAL_DELAY);

        database.write(context -> context.update(BOOKMARKS)
            .set(BOOKMARKS.SCHEDULED_REMOVAL_AT, scheduledRemovalAt)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .execute());
    }

    void cancelUsersBookmarksRemoval(long authorID) {
        database.write(context -> context.update(BOOKMARKS)
            .setNull(BOOKMARKS.SCHEDULED_REMOVAL_AT)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .execute());
    }

    void cleanRemovalScheduledBookmarks() {
        database.write(context -> context.deleteFrom(BOOKMARKS)
            .where(BOOKMARKS.SCHEDULED_REMOVAL_AT.isNotNull(),
                    BOOKMARKS.SCHEDULED_REMOVAL_AT.lessThan(Instant.now()))
            .execute());
    }

    static MessageEmbed createSuccessEmbed(String content) {
        return new EmbedBuilder().setDescription(content).setColor(COLOR_SUCCESS).build();
    }

    static MessageEmbed createWarningEmbed(String content) {
        return new EmbedBuilder().setDescription(content).setColor(COLOR_WARNING).build();
    }

    static MessageEmbed createFailureEmbed(String content) {
        return new EmbedBuilder().setDescription(content).setColor(COLOR_FAILURE).build();
    }

}
