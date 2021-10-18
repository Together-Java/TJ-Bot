package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.JDA;
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
 * When triggered with {@code /ban del_days @user reason}, the bot will check if the user has perms.
 * Then it will check if itself has perms to ban. If it does it will check if the user is too
 * powerful or not. If the user is not then bot will ban the user and reply with
 * {@code Banned User!}.
 *
 */
public final class BanCommand extends SlashCommandAdapter {
    // the logger
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    private static final String USER_OPTION = "user";
    private static final String DELETE_MESSAGE_HISTORY_DAYS_OPTION = "delete-message-history-days";
    private static final String REASON_OPTION = "reason";

    /**
     * Creates an instance of the ban command.
     */
    public BanCommand() {
        super("ban", "Use this command to ban a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user which you want to ban", true)
            .addOption(OptionType.INTEGER, DELETE_MESSAGE_HISTORY_DAYS_OPTION,
                    "The messages in these days will be deleted. 0 to 7 days. 0 means no messages deleted",
                    true)
            .addOption(OptionType.STRING, REASON_OPTION, "The reason of the ban", true);
    }


    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        JDA jda = event.getJDA();

        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();

        Member author = Objects.requireNonNull(event.getMember());

        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();

        long userId = Objects.requireNonNull(user).getUser().getIdLong();

        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You do not have the required permissions to ban users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("I don't have the required permissions to ban users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!bot.canInteract(Objects.requireNonNull(user))) {
            event.reply("This user is too powerful for me to ban.").setEphemeral(true).queue();
            return;
        }


        int deleteMessageHistoryDays =
                (int) Objects.requireNonNull(event.getOption("delete-message-history-days"))
                    .getAsLong();

        if (deleteMessageHistoryDays < 0 || deleteMessageHistoryDays > 7) {
            event.reply("The deletion days of the messages must be between 0 and 7 days.")
                .setEphemeral(true)
                .queue();
            return;
        }

        // tells ths user he has been banned
        jda.openPrivateChannelById(userId)
            .flatMap(channel -> channel
                .sendMessage("You have been banned for this reason " + reason))
            .queue();

        // Ban the user and send a success response
        event.getGuild()
            .ban(user, deleteMessageHistoryDays, reason)
            .flatMap(v -> event.reply("Banned the user " + user.getUser().getAsTag()))
            .queue();

        // Add this to audit log
        logger.info(
                "Bot '{}' was made to banned the user '{}' by '{}' and deleted the message history of the last '{}' days. Reason was '{}'",
                bot, user, author, deleteMessageHistoryDays, reason);
    }
}
