package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;


/**
 * When triggered with {@code /prune "number_of_messages"}, the bot will check
 * if the user has perms. Then it will check if itself has perms to delete messages.
 * If it does the bot will delete that amount of messages.
 * {@code Banned User!}.
 *
 */
public class DeleteMessageCommand extends SlashCommandAdapter {
    private static final String NUMBER_OF_MESSAGES = "number_of_messages";


    protected DeleteMessageCommand(@NotNull String name, @NotNull String description, SlashCommandVisibility visibility) {
        super("prune", "Use this command to delete a batch of messages", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.INTEGER, NUMBER_OF_MESSAGES, "The number of messages you want to delete. 1 to 100",
                true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final Member author = event.getMember();

        final TextChannel channel = event.getTextChannel();

        if (!Objects.requireNonNull(author).hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("You are missing permission to manage the message")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        final Member bot = event.getGuild().getSelfMember();

        if (!bot.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("I am missing permissions to manage these messages")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int amount = (int) Objects.requireNonNull(event.getOption(NUMBER_OF_MESSAGES)).getAsLong();

        var messageHistory = channel.getHistory().retrievePast(amount).complete();

        if(amount < 100) {
            event.reply("You can only delete 1 to 100 messages")
                    .setEphemeral(true)
                    .queue();
        } else {
            channel.purgeMessages(messageHistory);
        }
    }
}
