package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellEvalAbortionCause;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.SnippetStatus;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static org.togetherjava.tjbot.features.utils.Colors.ERROR_COLOR;
import static org.togetherjava.tjbot.features.utils.Colors.PARTIAL_SUCCESS_COLOR;
import static org.togetherjava.tjbot.features.utils.Colors.SUCCESS_COLOR;

class RendererUtils {
    private RendererUtils() {}

    static String abortionCauseToString(JShellEvalAbortionCause abortionCause) {
        return switch (abortionCause) {
            case JShellEvalAbortionCause.TimeoutAbortionCause ignored -> "Allowed time exceeded.";
            case JShellEvalAbortionCause.UnhandledExceptionAbortionCause(String exceptionClass, String exceptionMessage) ->
                "Uncaught exception:\n" + exceptionClass + ":" + exceptionMessage;
            case JShellEvalAbortionCause.CompileTimeErrorAbortionCause(List<String> errors) ->
                "The code doesn't compile:\n" + String.join("\n", errors);
            case JShellEvalAbortionCause.SyntaxErrorAbortionCause ignored ->
                "The code doesn't compile, there are syntax errors in this code.";
        };
    }

    enum GeneralStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        ERROR
    }

    static GeneralStatus getGeneralStatus(JShellResult result) {
        if (result.snippetsResults().isEmpty() && result.abortion() == null) {
            return GeneralStatus.SUCCESS; // Empty = success
        }
        if (result.snippetsResults().isEmpty()) {
            return GeneralStatus.ERROR; // Only abortion = failure, special case for syntax error
        }
        if (result.snippetsResults().size() == 1 && result.abortion() != null // Only abortion =
                                                                              // failure, case for
                                                                              // all except syntax
                                                                              // error
                && !(result.abortion()
                    .cause() instanceof JShellEvalAbortionCause.SyntaxErrorAbortionCause)) {
            return GeneralStatus.ERROR;
        }

        if (result.abortion() != null) {
            return GeneralStatus.PARTIAL_SUCCESS; // At least one snippet is a success
        }

        return getGeneralStatus(result.snippetsResults().getLast().status());
    }

    static Color getStatusColor(JShellResult result) {
        return switch (RendererUtils.getGeneralStatus(result)) {
            case SUCCESS -> SUCCESS_COLOR;
            case PARTIAL_SUCCESS -> PARTIAL_SUCCESS_COLOR;
            case ERROR -> ERROR_COLOR;
        };
    }

    private static GeneralStatus getGeneralStatus(SnippetStatus status) {
        return switch (status) {
            case VALID -> GeneralStatus.SUCCESS;
            case RECOVERABLE_DEFINED, RECOVERABLE_NOT_DEFINED -> GeneralStatus.PARTIAL_SUCCESS;
            case REJECTED -> GeneralStatus.ERROR;
        };
    }

    static Optional<EmbedBuilder> generateEmbed(List<String> segments) {
        StringBuilder currentEmbedDescription = new StringBuilder();
        for (String segment : segments) {
            if (currentEmbedDescription.length() + "\n".length()
                    + segment.length() < MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                currentEmbedDescription.append(segment).append("\n");
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(new EmbedBuilder().setDescription(currentEmbedDescription));
    }

    static MessageEmbed setMetadataAndBuild(EmbedBuilder embedBuilder, @Nullable String author,
            Color color) {
        if (author != null) {
            embedBuilder.setAuthor(author);
        }
        embedBuilder.setColor(color);
        return embedBuilder.build();
    }

    static String stdoutToMarkdownString(JShellResult result) {
        if (result.stdout().isEmpty()) {
            return "## System out\n[Nothing]\n";
        } else {
            return "## System out\n```\n" + getSdtOut(result) + "```";
        }
    }

    static String getSdtOut(JShellResult result) {
        String stdout = result.stdout();
        if (result.stdoutOverflow()) {
            stdout += MessageUtils.ABBREVIATION;
        }
        return stdout;
    }
}
