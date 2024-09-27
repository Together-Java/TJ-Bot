package org.togetherjava.tjbot.features.jshell.aws;

/**
 * A record containing the code snippet to be evaluated by the AWS JShell API
 * 
 * @param code The Java code snippet to execute
 *
 * @author Suraj Kumar
 */
public record JShellRequest(String code) {
}
