package org.togetherjava.tjbot.commands.generic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.example.AbstractCommand;

/**
 * Ping Pong command.
 */
public class PingCommand extends AbstractCommand {
    private static final Logger logger = LoggerFactory.getLogger(PingCommand.class);

    public PingCommand() {
        super("Ping Pong",
                "This command returns a message with the value `pong`, to show the user that the bot is up and running.",
                false);
    }

    /**
     * When the slash command is `/ping`, then the bot returns with the value `Pong!`
     * @param event The relating {@link SlashCommandEvent}
     */
    /**
     * Handler for the {@code /ping} command. Will ignore any message that is not exactly
     * {@code /ping}.
     *
     * @param event the event the message belongs to
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (!"/ping".equals(event.getCommandString())) {
            return;
        }

        logger.info("#{}: Received 'ping' command", event.getResponseNumber());

        event.getChannel().sendMessage("Pong!").queue();
    }
}
