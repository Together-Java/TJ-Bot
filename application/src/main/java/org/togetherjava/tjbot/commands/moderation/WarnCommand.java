package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.DatabaseException;

import java.awt.*;
import java.util.Objects;
import java.util.Optional;


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

        getData()
                .addOption(OptionType.USER, WARN_USER_OPTION, "The user to warn", true)
                .addOption(OptionType.STRING, WARN_REASON_OPTION, "The reason for the warning", true);
    }

    /**
     * Handles {@code /warn retrieve_warns user} commands. Retrieves the timesWarned value and then
     * sends that value to the user as an embed.
     * <p>
     * This command can only be used by users with the {@code BAN_MEMBERS} permission.
     *
     * @param event the event of the command
     */

    /**
     * Handles {@code /warn warn_user user reason} command. Saves the value under the given user,
     * given guild and add +1 to the number of warns the user has.
     * <p>
     * This command can only be used by users with the {@code BAN_MEMBERS} permission.
     *
     * @param event the event of the command
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping userOption =
                Objects.requireNonNull(event.getOption(WARN_USER_OPTION), "The target is null");
        Member author = userOption.getAsMember();
        Guild guild = Objects.requireNonNull(event.getGuild());

        // To prevent people from using warn content, only users with
        // elevated permissions are allowed to use this command
        if (handleHasPermissions(author, event, guild).equals(false)) {
            return;
        }

        // /warn warn_user @Zabuzard who knows
        User target = userOption.getAsUser();
        String reason = Objects.requireNonNull(event.getOption(WARN_REASON_OPTION)).getAsString();
        // TODO double check this part
        int warningAmount = 1;

        String userId = target.getId();
        dmUser(event.getJDA(), userId, reason, guild);

        /**
        try {
            database.write(context -> {
                WarnsRecord warnRecord =
                        context.newRecord(Warns.WARNS).setUserid(userId).setGuildid(guild.getId());
                // .setTimeswarned(warningAmount);
                logger.info("The member '{}' ({}) warned the user '{}' ({}) for the reason '{}'",
                        author.getUser().getAsTag(), author.getId(), target.getAsTag(),
                        target.getId(), reason);
                if (warnRecord.update() == 0) {
                    warnRecord.insert();
                }
            });
        } finally {
            event.reply("Warned " + target.getAsMention() + " for " + reason).queue();
        }
         */
    }

    private static void dmUser(@NotNull JDA jda, @NotNull String userId, @NotNull String reason,
            @NotNull Guild guild) {
        jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been warned by the server %s.
                            The reason for the warn is: %s
                            """
                        .formatted(guild.getName(), reason)))
            .queue();
    }

    private static Boolean handleHasPermissions(@NotNull Member author,
            @NotNull CommandInteraction event, @NotNull Guild guild) {
        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need the BAN_MEMBERS permission to use this command")
                .setEphemeral(true)
                .queue();
            return false;
        }
        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need the BAN_MEMBERS permission to use this command")
                .setEphemeral(true)
                .queue();
            return false;
        }
        return true;
    }
}
