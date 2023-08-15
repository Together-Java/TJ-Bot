package org.togetherjava.tjbot.features.jshell.backend.dto;

/**
 * The status of the snippet, see {@link jdk.jshell.Snippet.Status} for most of them, and evaluation
 * timeout of the JShell REST API for {@link SnippetStatus#ABORTED ABORTED}.
 */
public enum SnippetStatus {
    /**
     * See {@link jdk.jshell.Snippet.Status#VALID}.
     */
    VALID,
    /**
     * See {@link jdk.jshell.Snippet.Status#RECOVERABLE_DEFINED}.
     */
    RECOVERABLE_DEFINED,
    /**
     * See {@link jdk.jshell.Snippet.Status#RECOVERABLE_NOT_DEFINED}.
     */
    RECOVERABLE_NOT_DEFINED,
    /**
     * See {@link jdk.jshell.Snippet.Status#REJECTED}.
     */
    REJECTED,
    /**
     * Used when the timeout of an evaluation is reached.
     */
    ABORTED
}
