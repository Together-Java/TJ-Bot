package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;


public final class PinAnswerCommand extends BotCommandAdapter implements MessageContextCommand {
    private static final String COMMAND_NAME = "pin-answer";

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
            .queue(success -> event.reply("Answer pinned successfully!").setEphemeral(true).queue(),
                    failure -> event.reply("Failed to pin the message.")
                        .setEphemeral(true)
                        .queue());
    }

}
