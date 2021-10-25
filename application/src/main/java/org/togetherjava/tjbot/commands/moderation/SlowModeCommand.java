package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.managers.ChannelManager;
import net.dv8tion.jda.api.managers.Manager;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;

public class SlowModeCommand extends SlashCommandAdapter {
    private static final String NUMBER_OF_SECONDS = "number_of_seconds";

    public SlowModeCommand() {
        super("slow_mode", "Use this command to set a slow mode", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.INTEGER, NUMBER_OF_SECONDS, "Number of seconds you want to set the slow mode to", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final Member author = event.getMember();

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
            return;
        }

        int slowModeTime = Math.toIntExact(
                Objects.requireNonNull(event.getOption(NUMBER_OF_SECONDS)).getAsLong());

        Objects.requireNonNull(event.getGuild().getDefaultChannel()).getManager().setSlowmode(slowModeTime);
    }
}
