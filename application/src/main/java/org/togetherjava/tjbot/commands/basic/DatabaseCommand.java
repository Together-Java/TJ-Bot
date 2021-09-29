package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.example.AbstractCommand;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.DatabaseException;
import org.togetherjava.tjbot.db.generated.tables.Storage;
import org.togetherjava.tjbot.db.generated.tables.records.StorageRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of an example command to illustrate how to use a database.
 * <p>
 * The implemented command is {@code /db}. It has two subcommands {@code get} and {@code put}. It
 * acts like some sort of simple {@code Map<String, String>}, allowing the user to store and
 * retrieve key-value pairs from the database.
 * <p>
 * For example:
 * 
 * <pre>
 * {@code
 * /db put hello Hello World!
 * // TJ-Bot: Saved under 'hello'.
 *
 * /db get hello
 * // TJ-Bot: Saved message: Hello World!
 * }
 * </pre>
 */
public final class DatabaseCommand extends AbstractCommand {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseCommand.class);
    private static final String GET_COMMAND = "get";
    private static final String PUT_COMMAND = "put";
    private static final String KEY_OPTION = "key";
    private static final String VALUE_OPTION = "value";
    private final Database database;

    /**
     * Creates a new database command, using the given database.
     *
     * @param database the database to store the key-value pairs in
     */
    public DatabaseCommand(Database database) {
        super("db", "Storage and retrieval of key-value pairs", true);
        this.database = database;
    }

    @Override
    public @NotNull CommandData addOptions(@NotNull CommandData commandData) {
        return commandData.addSubcommands(
                new SubcommandData(GET_COMMAND,
                        "Gets a value corresponding to a key from a database").addOption(
                                OptionType.STRING, KEY_OPTION, "the key of the value to retrieve",
                                true),
                new SubcommandData(PUT_COMMAND,
                        "Puts a key-value pair into a database for later retrieval")
                            .addOption(OptionType.STRING, KEY_OPTION,
                                    "the key of the value to save", true)
                            .addOption(OptionType.STRING, VALUE_OPTION, "the value to save", true));
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        logger.info("#{}: Received '/db' command", event.getResponseNumber());

        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case GET_COMMAND -> handleGetCommand(event);
            case PUT_COMMAND -> handlePutCommand(event);
            default -> throw new AssertionError();
        }
    }

    /**
     * Handles {@code /db get key} commands. Retrieves the value saved under the given key and
     * responds with the results to the user.
     *
     * @param event the event of the command
     */
    private void handleGetCommand(CommandInteraction event) {
        // /db get hello
        String key = Objects.requireNonNull(event.getOption(KEY_OPTION)).getAsString();
        try {
            Optional<String> value = database.read(context -> {
                try (var select = context.selectFrom(Storage.STORAGE)) {
                    return Optional.ofNullable(select.where(Storage.STORAGE.KEY.eq(key)).fetchOne())
                        .map(StorageRecord::getValue);
                }
            });
            if (value.isEmpty()) {
                event.reply("Nothing found for the key '" + key + "'").setEphemeral(true).queue();
                return;
            }

            event.reply("Saved message: " + value.orElseThrow()).queue();
        } catch (DatabaseException e) {
            logger.error("Failed to get message", e);
            event.reply("Sorry, something went wrong.").setEphemeral(true).queue();
        }
    }

    /**
     * Handles {@code /db put key value} commands. Saves the value under the given key and responds
     * with the results to the user.
     * <p>
     * This command can only be used by users with the {@code MESSAGE_MANAGE} permission.
     *
     * @param event the event of the command
     */
    private void handlePutCommand(CommandInteraction event) {
        // To prevent people from saving malicious content, only users with
        // elevated permissions are allowed to use this command
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MESSAGE_MANAGE)) {
            return;
        }

        // /db put hello Hello World!
        String key = Objects.requireNonNull(event.getOption(KEY_OPTION)).getAsString();
        String value = Objects.requireNonNull(event.getOption(VALUE_OPTION)).getAsString();

        try {
            database.write(context -> {
                StorageRecord storageRecord =
                        context.newRecord(Storage.STORAGE).setKey(key).setValue(value);
                if (storageRecord.update() == 0) {
                    storageRecord.insert();
                }
            });

            event.reply("Saved under '" + key + "'.").queue();
        } catch (DatabaseException e) {
            logger.error("Failed to put message", e);
            event.reply("Sorry, something went wrong.").setEphemeral(true).queue();
        }
    }
}
