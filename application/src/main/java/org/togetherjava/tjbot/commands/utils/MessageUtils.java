package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
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

}
