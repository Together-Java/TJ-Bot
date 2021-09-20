package org.togetherjava.tjbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.DatabaseException;
import org.togetherjava.tjbot.db.generated.tables.Storage;
import org.togetherjava.tjbot.db.generated.tables.records.StorageRecord;

import java.util.Optional;

/**
 * Implementation of an example command to illustrate how to use a database.
 * <p>
 * The implemented commands are {@code !dbput} and {@code !dbget}. They act like some sort of simple
 * {@code Map<String, String>}, allowing the user to store and retrieve key-value pairs from the
 * database.
 * <p>
 * For example:
 *
 * <pre>
 * {@code
 * !dbput hello Hello World!
 * // TJ-Bot: Saved under 'hello'.
 * !dbget hello
 * // TJ-Bot: Saved message: Hello World!
 * }
 * </pre>
 */
// TODO: Remove this class after #127 has been merged
public final class DatabaseListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseListener.class);

    private final Database database;

    /**
     * Creates a new command listener, using the given database.
     *
     * @param database the database to store the key-value pairs in
     */
    public DatabaseListener(Database database) {
        this.database = database;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (!event.isFromType(ChannelType.TEXT)) {
            return;
        }
        if (event.isWebhookMessage()) {
            return;
        }
        if (event.isFromType(ChannelType.PRIVATE)) {
            return;
        }
        if (!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            return;
        }

        String message = event.getMessage().getContentDisplay();
        if (message.startsWith("!dbput")) {
            handlePutMessage(message, event);
        } else if (message.startsWith("!dbget")) {
            handleGetMessage(message, event);
        }
    }

    /**
     * Handler for the {@code !dbput} command.
     * <p>
     * If the message is in the wrong format, it will respond to the user instead of throwing any
     * exceptions.
     *
     * @param message the message to react to. For example {@code !dbput hello Hello World!}.
     * @param event the event the message belongs to, mainly used to respond back to the user
     */
    private void handlePutMessage(String message, MessageReceivedEvent event) {
        // !dbput hello Hello World!
        logger.info("#{}: Received '!dbput' command", event.getResponseNumber());
        String[] data = message.split(" ", 3);
        if (data.length != 3) {
            event.getChannel()
                .sendMessage("Sorry, your message was in the wrong format, try '!dbput key value'")
                .queue();
            return;
        }
        String key = data[1];
        String value = data[2];

        try {
            database.writeTransaction(ctx -> {
                StorageRecord storageRecord =
                        ctx.newRecord(Storage.STORAGE).setKey(key).setValue(value);
                if (storageRecord.update() == 0) {
                    storageRecord.insert();
                }
            });

            event.getChannel().sendMessage("Saved under '" + key + "'.").queue();
        } catch (DatabaseException e) {
            logger.error("Failed to put message", e);
            event.getChannel().sendMessage("Sorry, something went wrong.").queue();
        }
    }

    /**
     * Handler for the {@code !dbget} command.
     * <p>
     * If the message is in the wrong format, it will respond to the user instead of throwing any
     * exceptions.
     *
     * @param message the message to react to. For example {@code !dbget hello}.
     * @param event the event the message belongs to, mainly used to respond back to the user
     */
    private void handleGetMessage(String message, MessageReceivedEvent event) {
        // !dbget hello
        logger.info("#{}: Received '!dbget' command", event.getResponseNumber());
        String[] data = message.split(" ", 2);
        if (data.length != 2) {
            event.getChannel()
                .sendMessage("Sorry, your message was in the wrong format, try '!dbget key'")
                .queue();
            return;
        }
        String key = data[1];

        try {
            // The lambda needs to be a block so the return type can distinguish between the two
            // read/write methods. This feels like a sonar bug.
            Optional<StorageRecord> foundValue = database.read(context -> { // NOSONAR
                return Optional.ofNullable(context.selectFrom(Storage.STORAGE)
                    .where(Storage.STORAGE.KEY.eq(key))
                    .fetchOne());
            });
            if (foundValue.isEmpty()) {
                event.getChannel().sendMessage("Nothing found for the key '" + key + "'").queue();
                return;
            }

            String value = foundValue.get().getValue();
            event.getChannel().sendMessage("Saved message: " + value).queue();
        } catch (DatabaseException e) {
            logger.error("Failed to get message", e);
            event.getChannel().sendMessage("Sorry, something went wrong.").queue();
        }
    }
}
