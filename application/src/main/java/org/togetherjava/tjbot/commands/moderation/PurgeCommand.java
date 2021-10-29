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
 * This command requires the user to
 */
public class PurgeCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PurgeCommand.class);
    private static final String FIRST_MESSAGE_ID = "first_message_id";
    private static final String LAST_MESSAGE_ID = "last_message_id";


    public PurgeCommand() {
        super("purge", "Delete specific messages using there ids.", SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.NUMBER, FIRST_MESSAGE_ID,
                    "The id of the message you want to delete from.", true)
            .addOption(OptionType.NUMBER, LAST_MESSAGE_ID,
                    "The id of the message you want to delete to.", true);
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
            event.reply(
                    "I am missing MESSAGE_MANAGE permission which means I am unable to delete messages in this server.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have MESSAGE_MANAGE permission on the server '{}' ",
                    event.getGuild().getId());
            return;
        }

        long firstMessageId = Objects.requireNonNull(event.getOption(FIRST_MESSAGE_ID)).getAsLong();
        long lastMessageId = Objects.requireNonNull(event.getOption(LAST_MESSAGE_ID)).getAsLong();

        deleteMessagesById(channel, author, firstMessageId, lastMessageId, event);
    }

    public static void deleteMessagesById(@NotNull TextChannel channel, @NotNull Member author,
            long firstMessageId, long lastMessageId, @NotNull SlashCommandEvent event) {

        channel.purgeMessagesById(firstMessageId, lastMessageId);

        event.reply("I have deleted the messages from " + firstMessageId + " to " + lastMessageId)
            .setEphemeral(true)
            .queue();

        logger.info(" '{}' deleted messages from '{}' till '{}", author, firstMessageId,
                lastMessageId);
    }
}
