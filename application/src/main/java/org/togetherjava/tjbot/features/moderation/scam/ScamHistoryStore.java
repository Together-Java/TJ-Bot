package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Message;
import org.jooq.Result;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.ScamHistoryRecord;
import org.togetherjava.tjbot.features.utils.Hashing;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

import static org.togetherjava.tjbot.db.generated.tables.ScamHistory.SCAM_HISTORY;

/**
 * Store for history of detected scam messages. Can be used to retrieve information about past
 * events and further processing and handling of scam. For example, to delete a group of duplicate
 * scam messages after a moderator confirmed that it actually is scam and decided for an action.
 * <p>
 * Scam has to be added to the store using {@link #addScam(Message, boolean)} and can then be used
 * to determine {@link #hasRecentScamDuplicate(Message)} or for further processing, such as
 * {@link #markScamDuplicatesDeleted(Message)}.
 * <p>
 * Entries are only kept for a certain amount of time and will be purged regularly by
 * {@link ScamHistoryPurgeRoutine}.
 * <p>
 * The store persists the actions and is thread safe.
 */
public final class ScamHistoryStore {
    private final Database database;
    private static final Duration RECENT_SCAM_DURATION = Duration.ofMinutes(15);
    private static final String HASH_METHOD = "SHA";

    /**
     * Creates a new instance.
     *
     * @param database containing the scam history to work with
     */
    public ScamHistoryStore(Database database) {
        this.database = database;
    }

    /**
     * Adds the given scam message to the store.
     *
     * @param scam the message to add
     * @param isDeleted whether the message is already, or about to get, deleted
     */
    public void addScam(Message scam, boolean isDeleted) {
        Objects.requireNonNull(scam);

        database.write(context -> context.newRecord(SCAM_HISTORY)
            .setSentAt(scam.getTimeCreated().toInstant())
            .setGuildId(scam.getGuild().getIdLong())
            .setChannelId(scam.getChannel().getIdLong())
            .setMessageId(scam.getIdLong())
            .setAuthorId(scam.getAuthor().getIdLong())
            .setContentHash(hashMessageContent(scam))
            .setIsDeleted(isDeleted)
            .insert());
    }

    /**
     * Marks all duplicates to the given scam message (i.e. same guild, author, content, ...) as
     * deleted.
     *
     * @param scam the scam message to mark duplicates for
     * @return identifications of all scam messages that have just been marked deleted, which
     *         previously have not been marked accordingly yet
     */
    public Collection<ScamIdentification> markScamDuplicatesDeleted(Message scam) {
        return markScamDuplicatesDeleted(scam.getGuild().getIdLong(), scam.getAuthor().getIdLong(),
                hashMessageContent(scam));
    }

    /**
     * Marks all duplicates to the given scam message as deleted.
     *
     * @param guildId the id of the guild to mark duplicates for
     * @param authorId the id of the author to mark duplicates for
     * @param contentHash a hash identifying the content of the message to mark duplicates for, as
     *        determined by {@link #hashMessageContent(Message)}
     * @return identifications of all scam messages that have just been marked deleted, which
     *         previously have not been marked accordingly yet
     */
    public Collection<ScamIdentification> markScamDuplicatesDeleted(long guildId, long authorId,
            String contentHash) {
        return database.writeAndProvide(context -> {
            Result<ScamHistoryRecord> undeletedDuplicates = context.selectFrom(SCAM_HISTORY)
                .where(SCAM_HISTORY.GUILD_ID.eq(guildId)
                    .and(SCAM_HISTORY.AUTHOR_ID.eq(authorId))
                    .and(SCAM_HISTORY.CONTENT_HASH.eq(contentHash))
                    .and(SCAM_HISTORY.IS_DELETED.isFalse()))
                .fetch();

            undeletedDuplicates
                .forEach(undeletedDuplicate -> undeletedDuplicate.setIsDeleted(true).update());

            return undeletedDuplicates.stream().map(ScamIdentification::ofDatabaseRecord).toList();
        });
    }

    /**
     * Whether there are recent (a few minutes) duplicates to the given scam message (i.e. same
     * guild, author, content, ...).
     *
     * @param scam the scam message to look for duplicates
     * @return whether there are recent duplicates
     */
    public boolean hasRecentScamDuplicate(Message scam) {
        Instant recentScamThreshold = Instant.now().minus(RECENT_SCAM_DURATION);

        return database.read(context -> context.fetchCount(SCAM_HISTORY,
                SCAM_HISTORY.SENT_AT.greaterOrEqual(recentScamThreshold)
                    .and(SCAM_HISTORY.GUILD_ID.eq(scam.getGuild().getIdLong()))
                    .and(SCAM_HISTORY.AUTHOR_ID.eq(scam.getAuthor().getIdLong()))
                    .and(SCAM_HISTORY.CONTENT_HASH.eq(hashMessageContent(scam))))) != 0;
    }

    /**
     * Deletes all scam records from the history, which have been sent earlier than the given time.
     *
     * @param olderThan all records older than this will be deleted
     */
    public void deleteHistoryOlderThan(Instant olderThan) {
        database.write(context -> context.deleteFrom(SCAM_HISTORY)
            .where(SCAM_HISTORY.SENT_AT.lessOrEqual(olderThan))
            .execute());
    }

    /**
     * Hashes the content of the given message to uniquely identify it.
     * 
     * @param message the message to hash
     * @return a text representation of the hash
     */
    public static String hashMessageContent(Message message) {
        return Hashing.bytesToHex(Hashing.hash(HASH_METHOD,
                message.getContentRaw().getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Identification of a scam message, consisting mostly of IDs that uniquely identify it.
     *
     * @param guildId the id of the guild the message was sent in
     * @param channelId the id of the channel the message was sent in
     * @param messageId the id of the message itself
     * @param authorId the id of the author who sent the message
     * @param contentHash the unique hash of the message content
     */
    public record ScamIdentification(long guildId, long channelId, long messageId, long authorId,
            String contentHash) {
        private static ScamIdentification ofDatabaseRecord(ScamHistoryRecord scamHistoryRecord) {
            return new ScamIdentification(scamHistoryRecord.getGuildId(),
                    scamHistoryRecord.getChannelId(), scamHistoryRecord.getMessageId(),
                    scamHistoryRecord.getAuthorId(), scamHistoryRecord.getContentHash());
        }
    }
}
