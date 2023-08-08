package org.togetherjava.tjbot.features.jshell;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.SnippetStatus;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.awt.Color;

import static org.togetherjava.tjbot.features.utils.Colors.*;

/**
 * Allows to render JShell results.
 */
class ResultRenderer {

    /**
     * Renders a JShell result to an embed.
     * 
     * @param originator the user from who to display snippet ownership
     * @param originalCode the original code to display
     * @param partOfSession if it was part of a regular session, or a one time session
     * @param result the JShell result
     * @param builder the embed builder
     * @return the ember builder, for chaining
     */
    public EmbedBuilder renderToEmbed(@Nullable User originator, @Nullable String originalCode,
            boolean partOfSession, JShellResult result, EmbedBuilder builder) {
        if (originator != null) {
            builder.setAuthor(originator.getName() + "'s result");
        }
        builder.setColor(color(result.status()));

        if (originalCode != null
                && originalCode.length() + "```\n```".length() < MessageEmbed.VALUE_MAX_LENGTH) {
            builder.setDescription("```java\n" + originalCode + "```");
            builder.addField(
                    originator == null ? "Original code" : (originator.getName() + "'s code"),
                    "```java\n" + originalCode + "```", false);
        }

        if (result.result() != null && !result.result().isBlank()) {
            builder.addField("Snippet result", result.result(), false);
        }
        if (result.status() == SnippetStatus.ABORTED) {
            builder.setTitle("Request timed out");
        }

        String description = getDescriptionFromResult(result);
        description = MessageUtils.abbreviate(description, MessageEmbed.DESCRIPTION_MAX_LENGTH);
        if (result.stdoutOverflow() && !description.endsWith(MessageUtils.ABBREVIATION)) {
            description += MessageUtils.ABBREVIATION;
        }
        builder.setDescription(description);

        if (partOfSession) {
            builder.setFooter("Snippet " + result.id() + " of current session");
        } else {
            builder.setFooter("This result is not part of a session");
        }

        return builder;
    }

    private String getDescriptionFromResult(JShellResult result) {
        if (result.exception() != null) {
            return result.exception().exceptionClass() + ":"
                    + result.exception().exceptionMessage();
        }
        if (!result.errors().isEmpty()) {
            return String.join(", ", result.errors());
        }
        return result.stdout();
    }

    private Color color(SnippetStatus status) {
        return switch (status) {
            case VALID -> SUCCESS_COLOR;
            case RECOVERABLE_DEFINED, RECOVERABLE_NOT_DEFINED -> WARNING_COLOR;
            case REJECTED, ABORTED -> ERROR_COLOR;
        };
    }

}
