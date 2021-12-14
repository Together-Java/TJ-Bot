package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.awt.*;

/**
 * The command will inform tell the bots' latency to the user.
 */
public final class PingCommand extends SlashCommandAdapter {
    /**
     * Creates an instance of the ping pong command.
     */
    public PingCommand() {
        super("ping", "Informs the user of the latency of the bot", SlashCommandVisibility.GUILD);
    }

    /**
     * When triggered with {@code /ping}, the bot will respond with the latency of the bot.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event
            .replyEmbeds(new EmbedBuilder().setTitle("Here is the ping:")
                .setDescription("The WS ping is: " + event.getJDA().getGatewayPing() + "ms")
                .setColor(Color.decode("#895FE8"))
                .build())
            .queue();
    }
}
