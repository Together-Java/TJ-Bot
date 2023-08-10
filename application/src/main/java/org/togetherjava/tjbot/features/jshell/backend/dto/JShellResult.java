package org.togetherjava.tjbot.features.jshell.backend.dto;

import jdk.jshell.SnippetEvent;

import javax.annotation.Nullable;

import java.util.List;

/**
 * Result of a JShell eval.
 * 
 * @param status {@link SnippetStatus status} of the snippet
 * @param type {@link SnippetType type} of the snippet
 * @param id {@link jdk.jshell.Snippet#id() id} of the snippet
 * @param source source code of the snippet
 * @param result {@link SnippetEvent#value() result} of the snippet, usually null if the source code
 *        wasn't executed or if an exception happened during the execution, see related doc
 * @param exception exception thrown by the executed code, null if no exception was thrown
 * @param stdoutOverflow if stdout has overflowed and was truncated
 * @param stdout what was printed by the snippet
 * @param errors the compilations errors of the snippet
 */
public record JShellResult(SnippetStatus status, SnippetType type, String id, String source,
        @Nullable String result, @Nullable JShellExceptionResult exception, boolean stdoutOverflow,
        String stdout, List<String> errors) {

    /**
     * The JShell result.
     * 
     * @param status status of the snippet
     * @param type type of the snippet
     * @param id id of the snippet
     * @param source source code of the snippet
     * @param result result of the snippet, nullable
     * @param exception thrown exception, nullable
     * @param stdoutOverflow if stdout has overflowed and was truncated
     * @param stdout what was printed by the snippet
     * @param errors the compilations errors of the snippet
     */
    public JShellResult {
        errors = List.copyOf(errors);
    }
}
