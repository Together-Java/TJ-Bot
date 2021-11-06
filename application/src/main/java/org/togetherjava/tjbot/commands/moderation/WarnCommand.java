package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Warns;
import org.togetherjava.tjbot.db.generated.tables.records.WarnsRecord;

import java.util.Objects;


public class WarnCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WarnCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private final Database database;

    /**
     * Creates a new Instance.
     */
    public WarnCommand(@NotNull Database database) {
        super("warn", "warns the user", SlashCommandVisibility.GUILD);
        this.database = database;

        getData().addOption(OptionType.USER, USER_OPTION, "The user to warn", true)
            .addOption(OptionType.STRING, REASON_OPTION, "The reason for the warning", true);
    }

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
                Objects.requireNonNull(event.getOption(USER_OPTION), "The user is null");
        Member author = userOption.getAsMember();
        Guild guild = Objects.requireNonNull(event.getGuild());

        // To prevent people from using warn content, only users with
        // elevated permissions are allowed to use this command
        if (!handleHasPermissions(author, event, guild)) {
            return;
        }

        // /warn warn_user @Zabuzard who knows
        User target = userOption.getAsUser();
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();
        int warningAmount = 1;

        Long userId = target.getIdLong();
        dmUser(event.getJDA(), target.getId(), reason, guild);


        try {
            database.write(context -> {
                WarnsRecord warnRecord = context.newRecord(Warns.WARNS)
                    .setUserid(userId)
                    .setGuildId(guild.getIdLong())
                    .setWarningAmount(warningAmount i++);
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
