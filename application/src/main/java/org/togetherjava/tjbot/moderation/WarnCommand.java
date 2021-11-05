package org.togetherjava.tjbot.moderation;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;

public class WarnCommand extends SlashCommandAdapter {
    private static final String WARN_USER = "warn_user";
    private static final String WARN_USER_OPTION = "user";
    private static final String WARN_REASON_OPTION = "reason";
    private static final String RETRIEVE_WARNS_OPTION = "retrieve_warns";
    private static final String RETRIEVE_USER_OPTION = "user";
    private final Database database;

    /**
     * Creates a new adapter with the given data.
     *
     *@param database the database to store the key-value pairs in
     */
    public WarnCommand(@NotNull Database database) {
        super("warn", "warns the user", SlashCommandVisibility.GUILD);
        this.database = database;

        getData().addSubcommands(
                new SubcommandData(WARN_USER,
                        "Used to warn the user").addOption(
                        OptionType.USER, WARN_USER_OPTION, "The user to warn",
                        true)
                        .addOption(OptionType.STRING, WARN_REASON_OPTION, "The reason for the warning",
                                true),
                new SubcommandData(RETRIEVE_WARNS_OPTION, "Uses to retrieve a warn for a member")
                        .addOption(OptionType.USER, RETRIEVE_USER_OPTION,
                                "The warns for the user you want", true));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
    }
}
