package org.togetherjava.tjbot.features.jshell.backend.dto;

import javax.annotation.Nullable;

import java.util.List;

/**
 * Result of a JShell eval.
 * 
 * @param status status of the snippet
 * @param type type of the snippet
 * @param id id of the snippet
 * @param source source code of the snippet
 * @param result result of the snippet
 * @param exception thrown exception
 * @param stdoutOverflow if stdout has overflowed and was truncated
 * @param stdout what was printed by the snippet
 * @param errors the compilations errors of the snippet
 */
public record JShellResult(SnippetStatus status, SnippetType type, String id, String source,
        @Nullable String result, @Nullable JShellExceptionResult exception, boolean stdoutOverflow,
        String stdout, List<String> errors) {

    public JShellResult {
        errors = List.copyOf(errors);
    }
}
