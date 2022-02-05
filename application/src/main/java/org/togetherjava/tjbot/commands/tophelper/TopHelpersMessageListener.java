package org.togetherjava.tjbot.commands.tophelper;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;

import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Listener that receives all sent help messages and puts them into the database for
 * {@link TopHelpersCommand} to pick them up.
 */
public final class TopHelpersMessageListener extends MessageReceiverAdapter {
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
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        addMessageRecord(event);
    }

    private void addMessageRecord(@NotNull MessageReceivedEvent event) {
        database.write(context -> context.newRecord(HELP_CHANNEL_MESSAGES)
            .setMessageId(event.getMessage().getIdLong())
            .setGuildId(event.getGuild().getIdLong())
            .setChannelId(event.getChannel().getIdLong())
            .setAuthorId(event.getAuthor().getIdLong())
            .setSentAt(event.getMessage().getTimeCreated().toInstant())
            .insert());
    }
}
