package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Utility methods for {@link Message}.
 * <p>
 * This class is meant to contain all utility methods for {@link Message} that can be used on all
 * other commands to avoid similar methods appearing everywhere.
 */
public enum MessageUtils {
    ;

    /**
     * Disables all the buttons that a message has. Disabling buttons deems it as not clickable to
     * the user who sees it.
     * <p>
     * This method already queues the changes for you and does not block in any way.
     *
     * @param message the message that contains at least one button
     * @throws IllegalArgumentException when the given message does not contain any button
     */
    public static void disableButtons(@NotNull Message message) {
        List<Button> buttons = message.getButtons();
        if (buttons.isEmpty()) {
            throw new IllegalArgumentException("Message must contain at least one button");
        }

        message
            .editMessageComponents(ActionRow.of(buttons.stream().map(Button::asDisabled).toList()))
            .queue();
    }


    /**
     * Escapes every markdown content in the given string.
     *
     * If the escaped message is sent to Discord, it will display the original message.
     *
     * @param text the text to escape
     * @return the escaped text
     */
    public static @NotNull String escapeMarkdown(@NotNull String text) {
        // NOTE Unfortunately the utility does not escape backslashes '\', so we have to do it
        // ourselves
        // NOTE It also does not properly escape three backticks '```', it makes it '\```' but we
        // need '\`\`\`'
        String beforeEscape = text.replace("\\", "\\\\");
        String afterEscape = MarkdownSanitizer.escape(beforeEscape);
        return afterEscape.replace("\\```", "\\`\\`\\`");
    }

}
