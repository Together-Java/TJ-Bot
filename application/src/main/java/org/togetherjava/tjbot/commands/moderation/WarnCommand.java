package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;

import java.util.Objects;

public final class WarnCommand extends SlashCommandAdapter {
    private static final String REASON_OPTION = "reason";
    private static final String USER_OPTION = "user";
    private final Database database;

    public WarnCommand(Database database) {
        super("warn", "Use this command to warn", SlashCommandVisibility.GUILD);
        getData().addOption(OptionType.USER, USER_OPTION, "The user which you want to warn", true)
            .addOption(OptionType.STRING, REASON_OPTION, "The reason of the warb", true);

        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();

        /*
         * try { database.write(context -> { StorageRecord storageRecord =
         * context.newRecord(Storage.STORAGE).setKey(user).setValue(reason); if
         * (storageRecord.update() == 0) { storageRecord.insert(); } });
         * 
         * event.reply("Saved under '" + key + "'.").queue(); } catch (DatabaseException e) {
         * logger.error("Failed to put message", e);
         * event.reply("Sorry, something went wrong.").setEphemeral(true).queue(); }
         */


    }
}
