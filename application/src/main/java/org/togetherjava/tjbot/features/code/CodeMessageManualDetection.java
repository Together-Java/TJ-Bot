package org.togetherjava.tjbot.features.code;

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import org.togetherjava.tjbot.features.BotCommandAdapter;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.MessageContextCommand;

/**
 * Context command to allow users to select messages that contain code. They are then registered at
 * {@link CodeMessageHandler} for further processing.
 */
public final class CodeMessageManualDetection extends BotCommandAdapter
        implements MessageContextCommand {
    private final CodeMessageHandler codeMessageHandler;

    /**
     * Creates a new instance.
     *
     * @param codeMessageHandler to register selected code messages at for further handling
     */
    public CodeMessageManualDetection(CodeMessageHandler codeMessageHandler) {
        super(Commands.message("code-actions"), CommandVisibility.GUILD);

        this.codeMessageHandler = codeMessageHandler;
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        event.reply("I registered the message as code-message, actions should appear now.")
            .setEphemeral(true)
            .queue();

        codeMessageHandler.addAndHandleCodeMessage(event.getTarget(), false);
    }
}
