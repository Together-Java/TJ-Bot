package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

/**
 * Ping Pong command.
 */
public final class PingCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PingCommand.class);

    public PingCommand() {
        super("ping", "Bot responds with 'Pong!'");
    }

    /**
     * When the slash command is `/ping`, then the bot returns with the value `Pong!`
     * 
     * @param event The relating {@link SlashCommandEvent}
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        logger.info("#{}: Received 'ping' command", event.getResponseNumber());

        event.reply("Pong!").queue();
    }
}
