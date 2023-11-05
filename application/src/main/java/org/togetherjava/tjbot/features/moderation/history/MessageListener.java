package org.togetherjava.tjbot.features.moderation.history;

import static org.togetherjava.tjbot.db.generated.Tables.MESSAGE_HISTORY;

import java.time.Instant;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

/**
 * Listens for new message throughout the guild, then stores some metadata for each message in a
 * database.
 */
public class MessageListener extends MessageReceiverAdapter {
    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database this database to record some metadata for each message received throughout
     *        guild.
     */
    public MessageListener(Database database) {
        this.database = database;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        updateHistory(event);
    }

    private void updateHistory(MessageReceivedEvent event) {
        long guildId = event.getGuild().getIdLong();
        long channelId = event.getChannel().getIdLong();
        String messageId = event.getMessageId();
        long authorId = event.getAuthor().getIdLong();

        database.write(context -> context.newRecord(MESSAGE_HISTORY)
            .setCreatedAt(Instant.now())
            .setGuildId(guildId)
            .setChannelId(channelId)
            .setMessageId(messageId)
            .setAuthorId(authorId)
            .insert());
    }
}
