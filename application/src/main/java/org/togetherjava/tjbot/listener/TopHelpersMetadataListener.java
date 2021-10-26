package org.togetherjava.tjbot.listener;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.util.JdaUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.togetherjava.tjbot.db.generated.tables.MessageMetadata.MESSAGE_METADATA;

/**
 * Listener responsible for persistence of text message metadata.
 */
public final class TopHelpersMetadataListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersMetadataListener.class);

    private static final int MESSAGE_METADATA_ARCHIVAL_DAYS = 30;

    private final Database database;

    /**
     * Creates a new message metadata listener, using the given database.
     *
     * @param database the database to store message metadata.
     */
    public TopHelpersMetadataListener(Database database) {
        this.database = database;
    }

    /**
     * Stores the relevant message metadata for on message received event.
     * 
     * @param event incoming message received event.
     */
    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        var channel = event.getChannel();
        if (!event.getAuthor().isBot() && !event.isWebhookMessage()
                && JdaUtils.isAHelpChannel(channel)) {
            var messageId = event.getMessage().getIdLong();
            var guildId = event.getGuild().getIdLong();
            var channelId = channel.getIdLong();
            var userId = event.getAuthor().getIdLong();
            var createTimestamp = event.getMessage().getTimeCreated().toEpochSecond();
            database.write(dsl -> {
                dsl.newRecord(MESSAGE_METADATA)
                    .setMessageId(messageId)
                    .setGuildId(guildId)
                    .setChannelId(channelId)
                    .setUserId(userId)
                    .setCreateTimestamp(createTimestamp)
                    .insert();
                int noOfRowsDeleted = dsl.deleteFrom(MESSAGE_METADATA)
                    .where(MESSAGE_METADATA.CREATE_TIMESTAMP.le(Instant.now()
                        .minus(MESSAGE_METADATA_ARCHIVAL_DAYS, ChronoUnit.DAYS)
                        .getEpochSecond()))
                    .execute();
                if (noOfRowsDeleted > 0) {
                    logger.debug(
                            "{} old records have been deleted based on archival criteria of {} days.",
                            noOfRowsDeleted, MESSAGE_METADATA_ARCHIVAL_DAYS);
                }
            });
        }
    }
}
