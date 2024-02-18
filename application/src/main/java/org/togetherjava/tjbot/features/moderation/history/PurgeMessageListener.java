package org.togetherjava.tjbot.features.moderation.history;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jooq.impl.UpdatableRecordImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.MessageHistoryRecord;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.togetherjava.tjbot.db.generated.Tables.MESSAGE_HISTORY;

/**
 * Listens for new messages throughout the guild and stores metadata for each message in a database.
 * <p>
 * Note: This class also trims database records if they reach a maximum records limit.
 * </p>
 */
public final class PurgeMessageListener extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PurgeMessageListener.class);
    private static final int MESSAGES_RECORDS_MAX_LIMIT = 7500;
    private static final int MESSAGES_RECORDS_THRESHOLD = MESSAGES_RECORDS_MAX_LIMIT - 100;
    private static final int RECORDS_TO_TRIM = 500;
    static AtomicInteger recordsCounter = new AtomicInteger(0);
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database this database to record some metadata for each message received throughout
     *        guild.
     */
    public PurgeMessageListener(Database database) {
        this.database = database;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (shouldIgnoreMessages(event)) {
            return;
        }

        updateHistory(event);
    }

    private void updateHistory(MessageReceivedEvent event) {
        long guildId = event.getGuild().getIdLong();
        long channelId = event.getChannel().getIdLong();
        long messageId = event.getMessageIdLong();
        long authorId = event.getAuthor().getIdLong();

        // if record count is still below threshold, normal writes otherwise some of the oldest
        // records are trimmed.
        if (canWriteToDB() && isBelowThreshold()) {
            database.write(context -> context.newRecord(MESSAGE_HISTORY)
                .setSentAt(Instant.now())
                .setGuildId(guildId)
                .setChannelId(channelId)
                .setMessageId(messageId)
                .setAuthorId(authorId)
                .insert());

            incrementRecordsCounterByOne();
        }

        else if (canWriteToDB() && !isBelowThreshold()) {
            trimHistory();
        } else {
            logger.debug("purge history reached limit");
        }
    }

    private boolean shouldIgnoreMessages(MessageReceivedEvent event) {
        return event.isWebhookMessage() || event.getAuthor().isBot();
    }

    private static boolean canWriteToDB() {
        return recordsCounter.get() < MESSAGES_RECORDS_MAX_LIMIT;
    }

    private static void incrementRecordsCounterByOne() {
        recordsCounter.getAndIncrement();
    }

    static void decrementRecordsCounterByOne() {
        recordsCounter.decrementAndGet();
    }

    static void decrementRecordsCounterByTrimCount() {
        recordsCounter.set(recordsCounter.get() - RECORDS_TO_TRIM);
    }

    private boolean isBelowThreshold() {
        return recordsCounter.get() <= MESSAGES_RECORDS_THRESHOLD;
    }

    private void trimHistory() {
        Stream<MessageHistoryRecord> messageRecords =
                database.writeAndProvide(context -> context.selectFrom(MESSAGE_HISTORY)
                    .orderBy(MESSAGE_HISTORY.SENT_AT)
                    .limit(RECORDS_TO_TRIM)
                    .stream());

        try (messageRecords) {
            messageRecords.forEach(UpdatableRecordImpl::delete);
            decrementRecordsCounterByTrimCount();
        } catch (Exception exception) {
            logger.error(
                    "Unknown error happened during delete operation during trim of records in purge message listener",
                    exception);
        }
    }
}
