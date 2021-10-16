package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;


/**
 * <p>
 * The implemented command is {@code @user /ban delete-message-history-days reason}, upon which the bot will ban the user.
 */
public class BanCommand extends SlashCommandAdapter {
    //the logger
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    /**
     * Creates an instance of the ban command.
     */
    public BanCommand() {
        super("ban", "Use this command to ban a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, "user", "The user which you want to ban", true)
            .addOption(OptionType.INTEGER, "delete-message-history-days", "The delete message history", true)
                .addOption(OptionType.STRING, "reason", "The reason of the ban", true);


    }

    /**
     * When triggered with {@code /ban del_days @user reason}, the bot will respond will check if the user
     * has perms. Then it will check if itself has perms to ban. If it does it will check if the user is the user
     * is too powerful or not. If the user is not then bot will ban the user.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        // Used to get the member
        final Member user = Objects.requireNonNull(event.getOption("user")).getAsMember();

        //Used for the reason of the ban
        final String reason = Objects.requireNonNull(event.getOption("reason")).getAsString();

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "You do not have the required permissions to ban users from this server.")
                .queue();
            return;
        }

        Member selfMember = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("I don't have the required permissions to ban users from this server.")
                .queue();
            return;
        }

        if (user != null && !selfMember.canInteract(user)) {
            event.reply("This user is too powerful for me to ban.").queue();
            return;
        }

        // Used to delete message history
        long option = Objects.requireNonNull(event.getOption("delete-message-history-days")).getAsLong();
        int delDays = (int) option;

        if(delDays < 1) {
            event.reply("The deletion days of the messages must be between 1 and 7 days");
        } else if(delDays > 7) {
            event.reply("The deletion days of the messages must be between 1 and 7 days");

        } else {
            delDays = Math.toIntExact(option);
        }



        assert user != null;
        logger.error("The user is not provided");

        //Add this to audit log
        logger.info("User '{}' banned user '{}' and deleted the message history of the last '{}' days. Reason was '{}'",
                selfMember, user, delDays, reason);

        // Ban the user and send a success response
        event.getGuild()
            .ban(user, delDays, reason)
            .flatMap(v -> event.reply("Banned the user " + user.getUser().getAsTag()))
            .queue();
    }
}
