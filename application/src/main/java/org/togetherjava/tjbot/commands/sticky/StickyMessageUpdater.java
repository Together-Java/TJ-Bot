package org.togetherjava.tjbot.commands.sticky;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.StickyMessageRecord;

import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.Tables.STICKY_MESSAGE;

/**
 * Implements the feature to update Sticked messages, by deleting and resending them.
 */
public final class StickyMessageUpdater extends MessageReceiverAdapter {
    private final Database database;

    /**
     * Creates a new Instance.
     *
     * @param database the database to get Sticky data from
     */
    public StickyMessageUpdater(Database database) {
        super(Pattern.compile(".*"));

        this.database = database;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        MessageChannelUnion channel = event.getChannel();

        StickyMessageRecord stickyMessageRecord = StickyUtils.getSticky(database, channel);
        if (stickyMessageRecord == null) {
            return;
        }

        if (event.getAuthor().equals(event.getJDA().getSelfUser())
                && event.getMessage().getContentRaw().equals(stickyMessageRecord.getText())) {
            return;
        }

        channel.deleteMessageById(stickyMessageRecord.getMessageId()).queue();
        channel.sendMessage(stickyMessageRecord.getText()).queue(this::updateStickyMessageId);
    }

    private void updateStickyMessageId(Message message) {
        database.write(context -> context.update(STICKY_MESSAGE)
            .set(STICKY_MESSAGE.MESSAGE_ID, message.getIdLong())
            .where(STICKY_MESSAGE.CHANNEL_ID.eq(message.getChannel().getIdLong()))
            .execute());
    }
}
