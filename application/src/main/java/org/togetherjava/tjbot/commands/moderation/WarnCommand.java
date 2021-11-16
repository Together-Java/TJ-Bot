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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.WarnSystem;
import org.togetherjava.tjbot.db.generated.tables.records.WarnSystemRecord;

import java.awt.*;
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
        User target = userOption.getAsUser();
        Member author = userOption.getAsMember();
        Guild guild = Objects.requireNonNull(event.getGuild());
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        if (!handleHasPermissions(author, event, guild)) {
            return;
        }

        dmUser(event.getJDA(), target.getId(), reason, guild);

        try {
            database.write(context -> {
                WarnSystemRecord warnSystemRecord = context.newRecord(WarnSystem.WARN_SYSTEM)
                    .setUserid(target.getIdLong())
                    .setGuildId(guild.getIdLong())
                    .setWarnReason(reason)
                    // TODO change this
                    .setWarningAmount(1);
                if (warnSystemRecord.update() == 0) {
                    warnSystemRecord.insert();
                }
            });
            logger.info("Saved the user '{}' to the ban system.", target.getAsTag());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void dmUser(@NotNull JDA jda, @NotNull String userId, @NotNull String reason,
            @NotNull Guild guild) {
        jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Warn")
                .setDescription("You have been warned in " + guild.getName() + " for " + reason)
                .setColor(Color.MAGENTA)
                .build()))
            .queue();
    }

    private static boolean handleHasPermissions(@NotNull Member author,
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
