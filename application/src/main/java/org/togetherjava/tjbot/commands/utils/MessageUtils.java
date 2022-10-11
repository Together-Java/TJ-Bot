package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

import java.util.List;

/**
 * Utility methods for {@link Message}.
 * <p>
 * This class is meant to contain all utility methods for {@link Message} that can be used on all
 * other commands to avoid similar methods appearing everywhere.
 */
public class MessageUtils {
    private MessageUtils() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    /**
     * Disables all the buttons that a message has. Disabling buttons deems it as not clickable to
     * the user who sees it.
     * <p>
     * This method already queues the changes for you and does not block in any way.
     *
     * @param message the message that contains at least one button
     * @throws IllegalArgumentException when the given message does not contain any button
     */
    public static void disableButtons(Message message) {
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
    public static String escapeMarkdown(String text) {
        // NOTE Unfortunately the utility does not escape backslashes '\', so we have to do it
        // ourselves
        // NOTE It also does not properly escape three backticks '```', it makes it '\```' but we
        // need '\`\`\`'
        String beforeEscape = text.replace("\\", "\\\\");
        String afterEscape = MarkdownSanitizer.escape(beforeEscape);
        return afterEscape.replace("\\```", "\\`\\`\\`");
    }

    /**
     * Converts a slash command text to a mentioned slash command, which you can directly click on
     * in Discord.
     *
     * @param guild the {@link Guild} that contains the command
     * @param commandPath the command's name with its optional subcommand
     * @return Formatted string for the mentioned slash command
     * @throws IllegalArgumentException when the command isn't found in the guild
     */
    public static RestAction<String> mentionSlashCommand(Guild guild, String commandPath) {
        String commandName = commandPath.split(" ", 2)[0];
        return guild.retrieveCommands().map(guildCommands -> {
            Command guildCommand = guildCommands.stream()
                .filter(c -> c.getName().equalsIgnoreCase(commandName))
                .findAny()
                .orElseThrow(
                        () -> new IllegalArgumentException("Command '%s' does not exist in guild %s"
                            .formatted(commandName, guild.getId())));
            return String.format("</%s:%s>", commandPath, guildCommand.getId());
        });
    }

}
