package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;

import java.util.Objects;

public class WarnCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WarnCommand.class);
    private static final String WARN_USER = "warn_user";
    private static final String WARN_USER_OPTION = "user";
    private static final String WARN_REASON_OPTION = "reason";
    private static final String RETRIEVE_WARNS_OPTION = "retrieve_warns";
    private static final String RETRIEVE_USER_OPTION = "user";
    private final Database database;

    /**
     * Creates a new adapter with the given data.
     *
     * @param database the database to store the key-value pairs in
     */
    public WarnCommand(@NotNull Database database) {
        super("warn", "warns the user", SlashCommandVisibility.GUILD);
        this.database = database;

        getData().addSubcommands(new SubcommandData(WARN_USER, "Used to warn the user")
            .addOption(OptionType.USER, WARN_USER_OPTION, "The user to warn", true)
            .addOption(OptionType.STRING, WARN_REASON_OPTION, "The reason for the warning", true),
                new SubcommandData(RETRIEVE_WARNS_OPTION, "Uses to retrieve a warn for a member")
                    .addOption(OptionType.USER, RETRIEVE_USER_OPTION,
                            "The warns for the user you want", true));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case WARN_USER -> handleWarnCommand(event);
            case RETRIEVE_WARNS_OPTION -> handleRetrieveWarnCommand(event);
            default -> throw new AssertionError();
        }
    }

    private void handleWarnCommand(@NotNull CommandInteraction event) {
        // To prevent people from using warn content, only users with
        // elevated permissions are allowed to use this command
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need the MESSAGE_MANAGE permission to use this command")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need the MESSAGE_MANAGE permission to use this command")
                .setEphemeral(true)
                .queue();
            return;
        }


        // /warn warn_user @Zabuzard who knows
        User target = Objects.requireNonNull(event.getOption(WARN_USER_OPTION)).getAsUser();
        String reason = Objects.requireNonNull(event.getOption(WARN_REASON_OPTION)).getAsString();


        /*
         * try { database.write(context -> { WarnsRecord warnRecord =
         * context.newRecord(Warns.STORAGE).setKey(key).setValue(value); if (warnRecord.update() ==
         * 0) { warnRecord.insert(); } }); } catch (DatabaseException e) {
         * logger.error("Failed to put message", e);
         * event.reply("Sorry, something went wrong.").setEphemeral(true).queue(); }
         */
    }

    private void handleRetrieveWarnCommand(@NotNull CommandInteraction event) {

    }
}
