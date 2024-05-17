package org.togetherjava.tjbot.features.jshell.backend.dto;

/**
 * Represents an abortion of a JShell snippet.
 * 
 * @param sourceCause the source which caused this abortion
 * @param remainingSource the remaining source code which couldn't be executed because of this
 *        abortion
 * @param cause the cause of this abortion
 */
public record JShellEvalAbortion(String sourceCause, String remainingSource,
        JShellEvalAbortionCause cause) {
}
