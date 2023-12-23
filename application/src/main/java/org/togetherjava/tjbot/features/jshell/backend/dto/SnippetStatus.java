package org.togetherjava.tjbot.features.jshell.backend.dto;

/**
 * The status of the snippet, see {@link jdk.jshell.Snippet.Status}.
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
    REJECTED;

    /**
     * Returns the name of the constant, with _ replaced by spaces.
     * 
     * @return the name of the constant, with _ replaced by spaces
     */
    @Override
    public String toString() {
        return name().replace('_', ' ');
    }
}
