package org.togetherjava.tjbot.features.jshell.backend.dto;

/**
 * Result of a JShell eval plus the session id.
 *
 * @param id the session of the id
 * @param result the JShell eval result
 */
public record JShellResultWithId(String id, JShellResult result) {
}
