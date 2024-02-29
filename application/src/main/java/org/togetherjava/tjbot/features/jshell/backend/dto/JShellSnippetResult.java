package org.togetherjava.tjbot.features.jshell.backend.dto;

import jdk.jshell.SnippetEvent;

import javax.annotation.Nullable;

/**
 * Result of a JShell eval of a snippet.
 *
 * @param status {@link SnippetStatus status} of the snippet
 * @param type {@link SnippetType type} of the snippet
 * @param id {@link jdk.jshell.Snippet#id() id} of the snippet
 * @param source source code of the snippet
 * @param result {@link SnippetEvent#value() result} of the snippet, usually null if the source code
 *        wasn't executed or if an exception happened during the execution, see related doc
 */
public record JShellSnippetResult(SnippetStatus status, SnippetType type, int id, String source,
        @Nullable String result) {
}
