package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.internal.utils.Checks;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.lang.management.ManagementFactory;

/**
 * Informs the use how long the bot has been running for.
 */
public class UpTimeCommand extends SlashCommandAdapter {
    /**
     * Constructs an instance.
     */
    public UpTimeCommand() {
        super("uptime", "Tells the user how long the bot has been up for",
                SlashCommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final long duration = ManagementFactory.getRuntimeMXBean().getUptime();

        final long years = duration / 31104000000L;
        final long months = duration / 2592000000L % 12;
        final long days = duration / 86400000L % 30;
        final long hours = duration / 3600000L % 24;
        final long minutes = duration / 60000L % 60;
        final long seconds = duration / 1000L % 60;


        String uptime = (years == 0 ? "" : "**" + years + "** Years, ")
                + (months == 0 ? "" : "**" + months + "** Months, ")
                + (days == 0 ? "" : "**" + days + "** Days, ")
                + (hours == 0 ? "" : "**" + hours + "** Hours, ")
                + (minutes == 0 ? "" : "**" + minutes + "** Minutes, ")
                + (seconds == 0 ? "" : "**" + seconds + "** Seconds, ");
        uptime = replaceLast(uptime, ", ", "");
        uptime = replaceLast(uptime, ",", " and");

        event
            .replyEmbeds(new EmbedBuilder().setTitle("Uptime")
                .setDescription("I have been up for " + uptime + ".")
                .build())
            .queue();
    }

    public static @NotNull String replaceLast(String text, String search, String replacement) {
        Checks.notBlank(text, "text");
        Checks.notBlank(search, "search");
        Checks.notNull(replacement, "replacement");

        final int index = text.lastIndexOf(search);

        // Search not found
        if (index == -1) {
            return text;
        }

        final String firstPart = text.substring(0, index);
        final String lastPart = text.substring(index + search.length());

        return firstPart + replacement + lastPart;
    }
}
