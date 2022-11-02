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
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.Bookmarks.BOOKMARKS;

/**
 * The bookmarks system provides methods to interact with the database. It also enables the
 * {@link BookmarksCommand} to request paginations from the {@link BookmarksPaginatorInteractor}.
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
        Objects.requireNonNull(listPaginationConsumer, "No list pagination consumer was provided");

        listPaginationConsumer.accept(event);
    }

    /**
     * Requests a remove pagination from the {@link BookmarksPaginatorInteractor}.
     *
     * @param event The command interaction event
     */
    void requestRemovePagination(GenericCommandInteractionEvent event) {
        Objects.requireNonNull(removePaginationConsumer,
                "No remove pagination consumer was provided");

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
        return database.read(context -> context.selectCount().from(BOOKMARKS).execute());
    }

    int getUserBookmarkCount(long authorID) {
        return database.read(context -> context.selectCount()
            .from(BOOKMARKS)
            .where(BOOKMARKS.AUTHOR_ID.eq(authorID))
            .execute());
    }

    void startDeletionPeriodForUser(long authorID) {
        Instant delete_at = Instant.now().plus(REMOVE_BOOKMARKS_AFTER_LEAVE_DELAY);

        database.write(context -> context.update(BOOKMARKS)
            .set(BOOKMARKS.DELETE_AT, delete_at)
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
