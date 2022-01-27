package org.togetherjava.tjbot.commands.tophelper;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;

import java.time.Instant;
import java.time.Period;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Listener that receives all sent help messages and puts them into the database for
 * {@link TopHelpersCommand} to pick them up.
 *
 * Also runs a cleanup routine to get rid of old entries. In general, it manages the database data
 * to determine top-helpers.
 */
public final class TopHelpersMessageListener extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersMessageListener.class);
    private static final Period DELETE_MESSAGE_RECORDS_AFTER = Period.ofDays(90);

    private final Database database;

    /**
     * Creates a new listener to receive all message sent in help channels.
     *
     * @param database to store message meta-data in
     */
    public TopHelpersMessageListener(@NotNull Database database) {
        super(Pattern.compile(Config.getInstance().getHelpChannelPattern()));
        this.database = database;
    }

    @Override
    public void onMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        addMessageRecord(event);
        // TODO Use a routine that runs every 4 hours for the deletion instead
        deleteOldMessageRecords();
    }

    private void addMessageRecord(@NotNull GuildMessageReceivedEvent event) {
        database.write(context -> context.newRecord(HELP_CHANNEL_MESSAGES)
            .setMessageId(event.getMessage().getIdLong())
            .setGuildId(event.getGuild().getIdLong())
            .setChannelId(event.getChannel().getIdLong())
            .setAuthorId(event.getAuthor().getIdLong())
            .setSentAt(event.getMessage().getTimeCreated().toInstant())
            .insert());
    }

    private void deleteOldMessageRecords() {
        int recordsDeleted =
                database.writeAndProvide(context -> context.deleteFrom(HELP_CHANNEL_MESSAGES)
                    .where(HELP_CHANNEL_MESSAGES.SENT_AT
                        .lessOrEqual(Instant.now().minus(DELETE_MESSAGE_RECORDS_AFTER)))
                    .execute());

        if (recordsDeleted > 0) {
            logger.debug(
                    "{} old help message records have been deleted because they are older than {}.",
                    recordsDeleted, DELETE_MESSAGE_RECORDS_AFTER);
        }
    }
}
