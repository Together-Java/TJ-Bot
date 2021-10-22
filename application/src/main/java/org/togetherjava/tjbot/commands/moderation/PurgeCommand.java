package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;


/**
 * When triggered with {@code /prune "number_of_messages"}, the bot will check if the user has
 * perms. Then it will check if itself has perms to delete messages. If it does the bot will delete
 * that amount of messages. {@code Banned User!}.
 *
 */
public final class PurgeCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    private static final String NUMBER_OF_MESSAGES_TO_DELETE = "number_of_messages";
    private static final String USER_MESSAGES = "user";

    public PurgeCommand() {
        super("purge", "Use this command to delete a batch of messages",
                SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.INTEGER, NUMBER_OF_MESSAGES_TO_DELETE,
                    "The number of messages you want to delete. 1 to 100", true)
            .addOption(OptionType.USER, USER_MESSAGES,
                    "If you want to delete messages for a specific user.", false);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final Member author = event.getMember();
        final TextChannel channel = event.getTextChannel();

        if (!Objects.requireNonNull(author).hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("You are missing MESSAGE_MANAGE permission to delete these the message")
                .setEphemeral(true)
                .queue();
            return;
        }

        final Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();

        if (!bot.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("I am missing MESSAGE_MANAGE permission to delete these messages")
                .setEphemeral(true)
                .queue();
            return;
        }

        int amount = Math.toIntExact(
                Objects.requireNonNull(event.getOption(NUMBER_OF_MESSAGES_TO_DELETE)).getAsLong());
        if (amount > 200 || amount < 1) {
            event.reply("You can only delete 1 to 200 messages").setEphemeral(true).queue();
        } else {
            var messageHistory = channel.getHistory().retrievePast(amount).complete();
            channel.purgeMessages(messageHistory);
            event.reply("I have deleted this amount of messages " + messageHistory);
            logger.info(" '{}' deleted this amount of messages '{}'", author, amount);
        }
    }
}
