package org.togetherjava.tjbot.commands.tophelper;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * Listener responsible for persistence of text message metadata.
 */
public final class TopHelpersMessageListener extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersMessageListener.class);

    private static final int MESSAGE_METADATA_ARCHIVAL_DAYS = 30;

    private final Database database;

    /**
     * Creates a new message metadata listener, using the given database.
     *
     * @param database the database to store message metadata.
     */
    public TopHelpersMessageListener(@NotNull Database database) {
        super(Pattern.compile(Config.getInstance().getHelpChannelPattern()));
        this.database = database;
    }

    @Override
    public void onMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        var channel = event.getChannel();
        if (!event.getAuthor().isBot() && !event.isWebhookMessage()) {
            var messageId = event.getMessage().getIdLong();
            var guildId = event.getGuild().getIdLong();
            var channelId = channel.getIdLong();
            var userId = event.getAuthor().getIdLong();
            var createTimestamp = event.getMessage().getTimeCreated().toInstant();
            database.write(dsl -> {
                dsl.newRecord(HelpChannelMessages.HELP_CHANNEL_MESSAGES)
                    .setMessageId(messageId)
                    .setGuildId(guildId)
                    .setChannelId(channelId)
                    .setAuthorId(userId)
                    .setSentAt(createTimestamp)
                    .insert();
                int noOfRowsDeleted = dsl.deleteFrom(HelpChannelMessages.HELP_CHANNEL_MESSAGES)
                    .where(HelpChannelMessages.HELP_CHANNEL_MESSAGES.SENT_AT
                        .le(Instant.now().minus(MESSAGE_METADATA_ARCHIVAL_DAYS, ChronoUnit.DAYS)))
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
