package org.togetherjava.tjbot.features.moderation.history;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.togetherjava.tjbot.db.generated.Tables.MESSAGE_HISTORY;

/**
 * Listens for new message throughout the guild, then stores some metadata for each message in a
 * database.
 */
public class PurgeMessageListener extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PurgeMessageListener.class);
    private static final int MESSAGES_RECORDS_LIMIT = 7500;
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

        if (canWriteToDB()) {
            database.write(context -> context.newRecord(MESSAGE_HISTORY)
                .setSentAt(Instant.now())
                .setGuildId(guildId)
                .setChannelId(channelId)
                .setMessageId(messageId)
                .setAuthorId(authorId)
                .insert());

            incrementRecordsCounter();
        } else {
            logger.warn("purge history reached limit");
        }
    }

    private boolean shouldIgnoreMessages(MessageReceivedEvent event) {
        return event.isWebhookMessage() || event.getAuthor().isBot();
    }

    private static boolean canWriteToDB() {
        return recordsCounter.get() <= MESSAGES_RECORDS_LIMIT;
    }

    private static void incrementRecordsCounter() {
        recordsCounter.getAndIncrement();
    }

    static void decrementRecordsCounter() {
        recordsCounter.decrementAndGet();
    }

}
