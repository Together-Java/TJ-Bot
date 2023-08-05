package org.togetherjava.tjbot.features.jshell.backend.dto;

import java.util.List;

public record JShellResult(SnippetStatus status, SnippetType type, String id, String source,
        String result, JShellExceptionResult exception, boolean stdoutOverflow, String stdout,
        List<String> errors) {

    public JShellResult {
        errors = List.copyOf(errors);
    }
}
