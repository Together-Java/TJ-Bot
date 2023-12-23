package org.togetherjava.tjbot.features.jshell.backend.dto;

import javax.annotation.Nullable;

import java.util.List;

/**
 * Result of a JShell eval.
 *
 * @param snippetsResults the results of each individual snippet
 * @param abortion represents an abortion, if any of the snippet couldn't end properly, preventing
 *        the following snippets to execute, can be null
 * @param stdoutOverflow if stdout has overflowed and was truncated
 * @param stdout what was printed by the snippet
 */
public record JShellResult(List<JShellSnippetResult> snippetsResults,
        @Nullable JShellEvalAbortion abortion, boolean stdoutOverflow, String stdout) {

    /**
     * The JShell result.
     *
     * @param snippetsResults the results of each individual snippet
     * @param abortion represents an abortion, if any of the snippet couldn't end properly,
     *        preventing the following snippets to execute, can be null
     * @param stdoutOverflow if stdout has overflowed and was truncated
     * @param stdout what was printed by the snippet
     */
    public JShellResult {
        snippetsResults = List.copyOf(snippetsResults);
    }
}
