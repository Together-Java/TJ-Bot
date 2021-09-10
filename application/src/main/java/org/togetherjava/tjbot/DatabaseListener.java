package org.togetherjava.tjbot;

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

public final class DatabaseListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseListener.class);

    private final Database database;

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

        String message = event.getMessage().getContentDisplay();
        if (message.startsWith("!dbput")) {
            handlePutMessage(message, event);
        } else if (message.startsWith("!dbget")) {
            handleGetMessage(message, event);
        }
    }

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
                StorageRecord record = ctx.newRecord(Storage.STORAGE).setKey(key).setValue(value);
                if (record.update() == 0) {
                    record.insert();
                }
            });

            event.getChannel().sendMessage("Saved under '" + key + "'.").queue();
        } catch (DatabaseException e) {
            logger.error("Failed to put message", e);
            event.getChannel().sendMessage("Sorry, something went wrong.").queue();
        }
    }

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
            Optional<StorageRecord> foundValue = database.read(ctx -> {
                return ctx.selectFrom(Storage.STORAGE)
                          .where(Storage.STORAGE.KEY.eq(key))
                          .stream()
                          .findFirst();
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
