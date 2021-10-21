package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.awt.*;
import java.util.Objects;


/**
 * This command can ban users and optionally remove their messages from the past days. Banning can
 * also be paired with a ban reason. The command will also try to DM the user to inform him about
 * the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either ban other users or
 * to ban the specific given user (for example a moderator attempting to ban an admin).
 *
 */
public final class BanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    private static final String USER_OPTION = "user";
    private static final String DELETE_MESSAGE_HISTORY_DAYS_OPTION = "delete-message-history-days";
    private static final String REASON_OPTION = "reason";

    /**
     * Creates an instance of the ban command.
     */
    public BanCommand() {
        super("ban", "Bans a given user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to ban", true)
            .addOption(OptionType.STRING, REASON_OPTION, "why the user should be banned", true)
            .addOptions(new OptionData(OptionType.INTEGER, DELETE_MESSAGE_HISTORY_DAYS_OPTION,
                    "the amount of days of the message history to delete, otherwise no messages are deleted.",
                    true)
                    .addChoice("1",1)
                    .addChoice("2",2)
                    .addChoice("3",3)
                    .addChoice("4",4)
                    .addChoice("5",5)
                    .addChoice("6",6)
                    .addChoice("7",7)
            );
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();
        Member author = Objects.requireNonNull(event.getMember());
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();

        long userId = user.getUser().getIdLong();

        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You do not have the BAN_MEMBERS permission to ban users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!author.canInteract(Objects.requireNonNull(user))) {
            event.reply(
                    "This user is too powerful for you to ban.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("I don't have the BAN_MEMBERS permission to ban users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!bot.canInteract(Objects.requireNonNull(user))) {
            event.reply(
                    "This user is too powerful for me to ban because he has more permissions than me.")
                .setEphemeral(true)
                .queue();
            return;
        }

        OptionMapping option = event.getOption(DELETE_MESSAGE_HISTORY_DAYS_OPTION);

        String userName = user.getId();
        String authorName = author.getId();
        if (option != null) {
            int days = Math.toIntExact(
                    Objects.requireNonNull(event.getOption(DELETE_MESSAGE_HISTORY_DAYS_OPTION))
                            .getAsLong());
            BanHelperMethods.deleteMessageHistory(days, event);
            BanHelperMethods.banGuild(user, reason, days, event);

            logger.info(
                    " '{}' banned the user '{}' and deleted the message history of the last '{}' days. Reason was '{}'",
                    authorName, userName, days, reason);
        } else {
            BanHelperMethods.openPrivateChannel(userId, reason, event);
            BanHelperMethods.banGuild(user, reason, 0, event);
            BanHelperMethods.logger(authorName, userName, 0, reason);
        }
    }
}
