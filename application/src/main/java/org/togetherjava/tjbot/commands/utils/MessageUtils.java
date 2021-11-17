package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
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
     * Generates an embed with the given content.
     *
     * @param title title of the embed or {@code null} if not desired
     * @param content content to display in the embed
     * @param user name of the user who requested the embed or {@code null} if no user requested
     *        this
     * @param ambientColor the ambient color of the embed or {@code null} for a default color
     * @return the generated embed
     */
    public static @NotNull MessageEmbed generateEmbed(@Nullable String title,
            @NotNull CharSequence content, @Nullable User user, @Nullable Color ambientColor) {
        return new EmbedBuilder().setTitle(title)
            .setDescription(content)
            .setTimestamp(Instant.now())
            .setFooter(user == null ? null : user.getName())
            .setColor(ambientColor)
            .build();
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
        return MarkdownSanitizer.escape(text.replace("\\", "\\\\"));
    }

    /**
     * Instead of always queuing the reply message you can just make a method which queues
     * automatically.
     *
     * @param message the message that you want to send
     */
    public static void reply(String message, @NotNull SlashCommandEvent event) {
        event.reply(message).queue();
    }

    public static void reply(String message, @NotNull CommandInteraction event) {
        event.reply(message).queue();
    }

    /**
     * Instead of always setting ephemeral as true making one method and using it makes life easier.
     *
     * @param message the message that you want to send
     * @param event The slash command event
     */
    public static void replyEphemeral(String message, @NotNull SlashCommandEvent event) {
        event.reply(message).setEphemeral(true).queue();
    }

    public static void replyEphemeral(String message, @NotNull CommandInteraction event) {
        event.reply(message).setEphemeral(true).queue();
    }

}
