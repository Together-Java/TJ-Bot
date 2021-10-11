package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

import java.util.List;

/**
 * Utility methods for {@link Message}.
 * <p>
 * This class is meant to contain all utility methods for {@link Message} that can be used on all
 * other commands to avoid similar methods appearing everywhere.
 */
public class MessageUtils {

    private MessageUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Disables all the buttons that a message has. Disabling buttons deems it as not clickable to
     * the user who sees it.
     *
     * @param message the message that contains at least one button
     * @throws IllegalArgumentException when the given message does not contain any button
     */
    public static void disableButtons(Message message) {
        List<Button> buttons = message.getButtons();
        if (buttons.isEmpty())
            throw new IllegalArgumentException("Message must contain at least one button");

        message
            .editMessageComponents(ActionRow.of(buttons.stream().map(Button::asDisabled).toList()))
            .queue();
    }

}
