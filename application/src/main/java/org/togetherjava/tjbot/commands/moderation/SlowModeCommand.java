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

public class SlowModeCommand extends SlashCommandAdapter {
    private static final String NUMBER_OF_SECONDS = "number_of_seconds";
    private static final Logger logger = LoggerFactory.getLogger(SlowModeCommand.class);

    public SlowModeCommand() {
        super("slow_mode", "Use this command to set a slow mode", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.INTEGER, NUMBER_OF_SECONDS, "Number of seconds you want to set the slow mode to", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final Member author = event.getMember();
        final TextChannel channel = event.getTextChannel();

        if (!Objects.requireNonNull(author).hasPermission(Permission.MANAGE_CHANNEL)) {
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
                    Objects.requireNonNull(event.getGuild()));
            return;
        }

        int slowModeTime = Math.toIntExact(
                Objects.requireNonNull(event.getOption(NUMBER_OF_SECONDS)).getAsLong());

        channel.getManager().setSlowmode(slowModeTime).queue();
    }
}
