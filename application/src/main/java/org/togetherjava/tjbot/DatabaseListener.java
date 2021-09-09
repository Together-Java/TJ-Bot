package org.togetherjava.tjbot;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class DatabaseListener extends ListenerAdapter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseListener.class);
    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS storage (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)";

    private final Connection connection = connectDatabase();

    private static final String PUT_KEY_VALUE =
            "INSERT OR REPLACE INTO storage(key, value) VALUES (?, ?)";
    private static final String GET_VALUE = "SELECT value FROM storage WHERE key = ? LIMIT 1";

    private static Connection connectDatabase() {
        try {
            Files.createDirectories(Path.of("db"));
            Connection connection = DriverManager.getConnection("jdbc:sqlite:db/database.db");

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_TABLE);
            }

            return connection;
        } catch (SQLException | IOException e) {
            throw new IllegalStateException(e);
        }
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

        try (PreparedStatement putKeyValue = connection.prepareStatement(PUT_KEY_VALUE)) {
            putKeyValue.setString(1, key);
            putKeyValue.setString(2, value);
            putKeyValue.executeUpdate();

            event.getChannel().sendMessage("Saved under '" + key + "'.").queue();
        } catch (SQLException e) {
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

        try (PreparedStatement getValue = connection.prepareStatement(GET_VALUE)) {
            getValue.setString(1, key);
            try (ResultSet results = getValue.executeQuery()) {
                if (!results.next()) {
                    event.getChannel()
                         .sendMessage("Nothing found for the key '" + key + "'")
                         .queue();
                    return;
                }

                String value = results.getString("value");
                event.getChannel().sendMessage("Saved message: " + value).queue();
            }
        } catch (SQLException e) {
            logger.error("Failed to get message", e);
            event.getChannel().sendMessage("Sorry, something went wrong.").queue();
        }
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
