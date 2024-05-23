package org.togetherjava.tjbot.features.mathcommands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;

import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.verify;

final class TeXCommandTest {
    private JdaTester jdaTester;
    private SlashCommand command;

    @BeforeEach
    void setUp() {
        jdaTester = new JdaTester();
        command = jdaTester.spySlashCommand(new TeXCommand());
    }

    private SlashCommandInteractionEvent triggerSlashCommand(String latex) {
        SlashCommandInteractionEvent event = jdaTester.createSlashCommandInteractionEvent(command)
            .setOption(TeXCommand.LATEX_OPTION, latex)
            .build();

        command.onSlashCommand(event);
        return event;
    }

    private void verifySuccessfulResponse(String query) {
        ArgumentMatcher<FileUpload> attachmentIsTexPng =
                attachment -> attachment != null && "tex.png".equals(attachment.getName());

        verify(jdaTester.getInteractionHookMock(), description("Testing query: " + query))
            .editOriginalAttachments(argThat(attachmentIsTexPng));
    }

    private static List<String> provideSupportedQueries() {
        List<String> fullLatex = List.of("\\frac{x}{2}", "f \\in \\mathcal{O}(n^2)",
                "a^{\\varphi(n)} \\equiv 1\\ (\\textrm{mod}\\ n)", "\\textrm{I like } \\xi");

        List<String> inlineLatex = List.of("$\\frac{x}{2}$", "$x$ hello", "hello $x$",
                "hello $x$ world $y$", "$x$$y$$z$", "$x \\cdot y$");

        List<String> edgeCases = List.of("", "   ", " \n ");

        List<String> allQueries = new ArrayList<>();
        allQueries.addAll(fullLatex);
        allQueries.addAll(inlineLatex);
        allQueries.addAll(edgeCases);

        return allQueries;
    }

    @ParameterizedTest
    @MethodSource("provideSupportedQueries")
    @DisplayName("The command supports and renders all supported latex queries")
    void canRenderSupportedQuery(String supportedQuery) {
        // GIVEN a supported latex query

        // WHEN triggering the command
        triggerSlashCommand(supportedQuery);

        // THEN the command send a successful response
        verifySuccessfulResponse(supportedQuery);
    }

    private static List<String> provideBadInlineQueries() {
        return List.of("hello $x world", "$", "  $  ", "hello $x$ world$", "$$$$$", "$x$$y$$z");
    }

    @ParameterizedTest
    @MethodSource("provideBadInlineQueries")
    @DisplayName("The command does not support bad inline latex queries, for example with missing dollars")
    void failsOnBadInlineQuery(String badInlineQuery) {
        // GIVEN a bad inline latex query

        // WHEN triggering the command
        SlashCommandInteractionEvent event = triggerSlashCommand(badInlineQuery);

        // THEN the command send a failure response
        verify(event, description("Testing query: " + badInlineQuery))
            .reply(contains(TeXCommand.INVALID_INLINE_FORMAT_ERROR_MESSAGE));
    }

    private static List<String> provideBadQueries() {
        return List.of("__", "\\foo", "\\left(x + y)");
    }

    @ParameterizedTest
    @MethodSource("provideBadQueries")
    @DisplayName("The command does not support bad latex queries, for example with unknown symbols or incomplete braces")
    void failsOnBadQuery(String badQuery) {
        // GIVEN a bad inline latex query

        // WHEN triggering the command
        SlashCommandInteractionEvent event = triggerSlashCommand(badQuery);

        // THEN the command send a failure response
        verify(event, description("Testing query: " + badQuery))
            .reply(startsWith(TeXCommand.BAD_LATEX_ERROR_PREFIX));
    }
}
