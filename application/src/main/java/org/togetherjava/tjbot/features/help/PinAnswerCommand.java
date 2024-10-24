package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;


public final class PinAnswerCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final String COMMAND_NAME = "pin-answer";
    private static final Logger logger = LoggerFactory.getLogger(PinAnswerCommand.class);

    /**
     * Creates a new instance.
     *
     */
    public PinAnswerCommand() {
        super(Commands.message(COMMAND_NAME), CommandVisibility.GUILD);


    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        Message originalMessage = event.getTarget();
        originalMessage.pin()
            .queue(success -> logger.debug("Message pinned successfully!"),
                    failure -> logger.debug(failure.getMessage()));

    }
}
