package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

import java.util.List;
import java.util.function.Supplier;

/**
 * Utility methods for {@link Message}.
 * <p>
 * This class is meant to contain all utility methods for {@link Message} that can be used on all
 * other commands to avoid similar methods appearing everywhere.
 */
public class MessageUtils {
    private static final String ABBREVIATION = "...";

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
     * <p>
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
     * Converts a guild slash command text to a mentioned slash command, which you can directly
     * click on in Discord.
     *
     * @param guild the {@link Guild} that contains the command
     * @param commandName the command's name
     * @param subCommands optional subcommand group & subcommand, depending on the base command used
     * @return Formatted string for the mentioned slash command
     * @throws IllegalArgumentException when the command isn't found in the guild
     */
    public static RestAction<String> mentionGuildSlashCommand(Guild guild, String commandName,
            String... subCommands) {
        RestAction<List<Command>> availableCommands = guild.retrieveCommands();
        Supplier<String> notFoundMessage = () -> "Command '%s' does not exist in guild %s"
            .formatted(commandName, guild.getId());

        return mentionSlashCommand(availableCommands, notFoundMessage, commandName, subCommands);
    }

    /**
     * Converts a global slash command text to a mentioned slash command, which you can directly
     * click on in Discord.
     *
     * @param jda to retrieve global commands from
     * @param commandName the command's name
     * @param subCommands optional subcommand group & subcommand, depending on the base command used
     * @return Formatted string for the mentioned slash command
     * @throws IllegalArgumentException when the global command was not found
     */
    public static RestAction<String> mentionGlobalSlashCommand(JDA jda, String commandName,
            String... subCommands) {
        RestAction<List<Command>> availableCommands = jda.retrieveCommands();
        Supplier<String> notFoundMessage =
                () -> "The global command '%s' does not exist".formatted(commandName);

        return mentionSlashCommand(availableCommands, notFoundMessage, commandName, subCommands);
    }

    private static RestAction<String> mentionSlashCommand(
            RestAction<? extends List<Command>> availableCommands, Supplier<String> notFoundMessage,
            String commandName, String... subCommands) {
        return availableCommands.map(commands -> {
            Command command = commands.stream()
                .filter(commandCandidate -> commandCandidate.getName()
                    .equalsIgnoreCase(commandName))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(notFoundMessage.get()));

            String commandPath = commandName;
            if (subCommands.length > 0) {
                commandPath += " " + String.join(" ", subCommands);
            }

            return String.format("</%s:%s>", String.join(" ", commandPath), command.getId());
        });
    }

    /**
     * Abbreviates the given text if it is too long.
     * <p>
     * Abbreviation is done by adding {@value ABBREVIATION}.
     *
     * @param text the text to abbreviate
     * @param maxLength the maximal length of the abbreviated text
     * @return the abbreviated text, guaranteed to be smaller than the given length
     */
    public static String abbreviate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        if (maxLength < ABBREVIATION.length()) {
            return text.substring(0, maxLength);
        }

        return text.substring(0, maxLength - ABBREVIATION.length()) + ABBREVIATION;
    }

    /**
     * Mentions a guild channel by its id. If the given channelId is unknown the formatted text will
     * say `#deleted-channel` in discord.
     *
     * @param channelId the ID of the channel to mention
     * @return the channel as formatted string which Discord interprets as clickable mention
     */
    public static String mentionChannelById(long channelId) {
        // Clone of JDAs Channel#getAsMention, but unfortunately channel instances can not be
        // created out of just an ID, unlike User#fromId
        return "<#%d>".formatted(channelId);
    }
}
