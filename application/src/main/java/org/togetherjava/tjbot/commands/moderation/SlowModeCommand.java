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
 * This command can set the slow mode for the channel the command is run in. If the slow mode is set
 * to 0 it is rest.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either manage channels.
 *
 */
public class SlowModeCommand extends SlashCommandAdapter {
    private static final String NUMBER_OF_SECONDS = "number_of_seconds";
    // The slow mode can not be in the negatives.
    private static final Integer MIN_SLOWMODE_SECONDS = 0;
    private static final Logger logger = LoggerFactory.getLogger(SlowModeCommand.class);

    public SlowModeCommand() {
        super("slow_mode", "Use this command to set a slow mode", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.INTEGER, NUMBER_OF_SECONDS,
                "Number of seconds you want to set the slow mode to", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final Member author = event.getMember();
        final TextChannel channel = event.getTextChannel();

        if (author.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You are missing MANAGE_CHANNEL permission to delete these the message")
                .setEphemeral(true)
                .queue();
            return;
        }

        final Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!bot.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("I am missing MANAGE_CHANNEL permission to delete these messages")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have BAN_MEMBERS permission on the server '{}' ",
                    event.getGuild());
            return;
        }

        int slowModeTime = Math
            .toIntExact(Objects.requireNonNull(event.getOption(NUMBER_OF_SECONDS)).getAsLong());

        if (slowModeTime < MIN_SLOWMODE_SECONDS || slowModeTime > TextChannel.MAX_SLOWMODE) {
            event.reply("The slow mode time can only be between 0 and 21600 seconds")
                .setEphemeral(true)
                .queue();
        }

        channel.getManager().setSlowmode(slowModeTime).queue();
        logger.info(
                " '{}' set the slow mode to '{}'",
                Objects.requireNonNull(event.getMember()), slowModeTime);
    }
}
