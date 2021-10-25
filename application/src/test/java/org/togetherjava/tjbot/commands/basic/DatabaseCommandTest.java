package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.jooq.Record1;
import org.jooq.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Storage;
import org.togetherjava.tjbot.db.generated.tables.records.StorageRecord;
import org.togetherjava.tjbot.jda.JdaTester;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

final class DatabaseCommandTest {

    private Database database;

    private static SlashCommandEvent createGet(@NotNull String value, @NotNull SlashCommand command,
            @NotNull JdaTester jdaTester) {
        return jdaTester.createSlashCommandEvent(command)
            .subcommand("get")
            .option("key", value)
            .build();
    }

    private static SlashCommandEvent createPut(@NotNull String key, @NotNull String value,
            @NotNull SlashCommand command, @NotNull JdaTester jdaTester) {
        return jdaTester.createSlashCommandEvent(command)
            .subcommand("put")
            .option("key", key)
            .option("value", value)
            .build();
    }

    @BeforeEach
    void setupDatabase() throws SQLException {
        // TODO This has to be done dynamically by the Flyway script, adjust gradle test settings
        database = new Database("jdbc:sqlite:");
        database.write(context -> {
            context.ddl(Storage.STORAGE).executeBatch();
        });
    }

    @Test
    void getNoKey() {
        SlashCommand command = new DatabaseCommand(database);
        JdaTester jdaTester = new JdaTester();

        SlashCommandEvent event = createGet("foo", command, jdaTester);
        command.onSlashCommand(event);

        verify(event, times(1)).reply("Nothing found for the key 'foo'");
    }

    @Test
    void getValidKey() {
        SlashCommand command = new DatabaseCommand(database);
        JdaTester jdaTester = new JdaTester();

        putIntoDatabase("foo", "bar");

        SlashCommandEvent event = createGet("foo", command, jdaTester);
        command.onSlashCommand(event);

        verify(event, times(1)).reply("Saved message: bar");
    }

    @Test
    void putEmpty() {
        SlashCommand command = new DatabaseCommand(database);
        JdaTester jdaTester = new JdaTester();

        SlashCommandEvent event = createPut("foo", "bar", command, jdaTester);
        command.onSlashCommand(event);

        verify(event, times(1)).reply("Saved under 'foo'.");
        assertValueInDatabase("foo", "bar");
    }

    @Test
    void putOverride() {
        SlashCommand command = new DatabaseCommand(database);
        JdaTester jdaTester = new JdaTester();

        SlashCommandEvent event = createPut("foo", "bar", command, jdaTester);
        command.onSlashCommand(event);

        event = createPut("foo", "baz", command, jdaTester);
        command.onSlashCommand(event);

        verify(event, times(1)).reply("Saved under 'foo'.");
        assertValueInDatabase("foo", "baz");
    }

    @Test
    void getPutGet() {
        SlashCommand command = new DatabaseCommand(database);
        JdaTester jdaTester = new JdaTester();

        SlashCommandEvent getEvent = createGet("foo", command, jdaTester);
        command.onSlashCommand(getEvent);
        verify(getEvent, times(1)).reply("Nothing found for the key 'foo'");

        SlashCommandEvent putEvent = createPut("foo", "bar", command, jdaTester);
        command.onSlashCommand(putEvent);
        verify(putEvent, times(1)).reply("Saved under 'foo'.");

        command.onSlashCommand(getEvent);
        verify(getEvent, times(1)).reply("Saved message: bar");
    }

    @Test
    void getOrPutWithNoTable() throws SQLException {
        SlashCommand command = new DatabaseCommand(new Database("jdbc:sqlite:"));
        JdaTester jdaTester = new JdaTester();

        SlashCommandEvent event = createGet("foo", command, jdaTester);
        command.onSlashCommand(event);
        verify(event, times(1)).reply("Sorry, something went wrong.");

        event = createPut("foo", "bar", command, jdaTester);
        command.onSlashCommand(event);
        verify(event, times(1)).reply("Sorry, something went wrong.");
    }

    private void assertValueInDatabase(@NotNull String key, @NotNull String value) {
        Result<Record1<String>> results = database.read(context -> {
            try (var select = context.select(Storage.STORAGE.VALUE)) {
                return select.from(Storage.STORAGE).where(Storage.STORAGE.KEY.eq(key)).fetch();
            }
        });
        assertEquals(1, results.size());
        assertEquals(value, results.get(0).get(Storage.STORAGE.VALUE));
    }

    private void putIntoDatabase(@NotNull String key, @NotNull String value) {
        database.write(context -> {
            StorageRecord storageRecord =
                    context.newRecord(Storage.STORAGE).setKey(key).setValue(value);
            if (storageRecord.update() == 0) {
                storageRecord.insert();
            }
        });
    }

}
