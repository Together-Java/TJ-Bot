package org.togetherjava.tjbot.command;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.AbstractJdaMock;
import org.togetherjava.tjbot.commands.Command;
import org.togetherjava.tjbot.commands.generic.DatabaseGetCommand;
import org.togetherjava.tjbot.commands.generic.DatabasePutCommand;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Storage;
import org.togetherjava.tjbot.db.generated.tables.records.StorageRecord;

import java.sql.SQLException;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseCommandTest extends AbstractJdaMock {

    private Database database;
    private SlashCommandEvent dbGetCommand;
    private SlashCommandEvent dbPutCommand;

    public DatabaseCommandTest() {
        super(2, 2);
    }

    @BeforeEach
    void setupDatabase() throws SQLException {
        super.setup();
        database = new Database("jdbc:sqlite:");

        // TODO - Remover this. We should use the flyway script, but I don't know a thing about
        // using it with Gradle
        database.writeTransaction(ctx -> {
            ctx.ddl(Storage.STORAGE).executeBatch();
        });

        dbGetCommand = createSlashCommand("dbget ditto");
        dbPutCommand = createSlashCommand("dbput ditto evolved");

        when(dbGetCommand.getChannelType()).thenReturn(ChannelType.TEXT);
        when(dbPutCommand.getChannelType()).thenReturn(ChannelType.TEXT);
    }

    @DisplayName("""
            Given an existing Database.
            When a user retrieves a non-existing command.
            Then an invalid message is returned
            """)
    @Test
    void testGetCommand() {

        Command command = new DatabaseGetCommand(database);

        command.onSlashCommand(dbGetCommand);

        verify(dbGetCommand, times(1)).reply("Nothing found for the key 'ditto'");
    }

    @DisplayName("""
            Given an existing Database with commands present
            When a user retrieves a command.
            Then the correct response is returned
            """)
    @Test
    void testGetWrongCommand() {

        database.writeTransaction(ctx -> {
            StorageRecord storageRecord =
                    ctx.newRecord(Storage.STORAGE).setKey("ditto").setValue("evolution");
            if (storageRecord.update() == 0) {
                storageRecord.insert();
            }
        });

        Command command = new DatabaseGetCommand(database);

        command.onSlashCommand(dbGetCommand);

        verify(dbGetCommand, times(1)).reply("Saved message: evolution");
    }

    @DisplayName("""
            Given an existing Database with commands present
            When a user updates a command.
            Then the correct response is returned and the command is updated
            """)
    @Test
    void testUpdateCommand() {
        SlashCommandEvent failedDbGetCommand = createSlashCommand("dbget pikachu");
        when(failedDbGetCommand.getChannelType()).thenReturn(ChannelType.TEXT);
        Command getCommand = new DatabaseGetCommand(database);
        Command putCommand = new DatabasePutCommand(database);

        putCommand.onSlashCommand(dbPutCommand);
        getCommand.onSlashCommand(failedDbGetCommand);
        getCommand.onSlashCommand(dbGetCommand);

        verify(dbPutCommand, times(1)).reply("Saved under 'ditto'.");
        verify(failedDbGetCommand, times(1)).reply("Nothing found for the key 'pikachu'");
        verify(dbGetCommand, times(1)).reply("Saved message: evolved");
    }

    @DisplayName("""
            Given an existing Database with commands present
            When a user inserts invalid messages
            Then the correct responses are returned
            """)
    @Test
    void testErrorMessages() {
        SlashCommandEvent errorCommand = createSlashCommand("dbget");
        when(errorCommand.getChannelType()).thenReturn(ChannelType.TEXT);
        Command command = new DatabaseGetCommand(database);

        command.onSlashCommand(errorCommand);

        verify(errorCommand, atLeastOnce())
            .reply("Sorry, your message was in the wrong format, try '/dbget key'");
    }

    @DisplayName("""
            Given an invalid Database
            When a user inserts messages
            Then the correct responses are returned
            """)
    @Test
    void testDatabaseErrorMessages() throws SQLException {
        Database database = new Database("jdbc:sqlite:");
        Command getCommand = new DatabaseGetCommand(database);
        Command putCommand = new DatabasePutCommand(database);

        getCommand.onSlashCommand(dbGetCommand);
        putCommand.onSlashCommand(dbPutCommand);

        verify(dbGetCommand, times(1)).reply("Sorry, something went wrong.");
        verify(dbPutCommand, times(1)).reply("Sorry, something went wrong.");
    }
}
