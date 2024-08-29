package org.togetherjava.tjbot.features.jshell.aws.exceptions;

/**
 * An exception that contains the HTTP status code and response body when the request to the JShell
 * AWS API fails.
 *
 * @author Suraj Kumar
 */
public class JShellAPIException extends RuntimeException {
    private final int statusCode;
    private final String body;

    public JShellAPIException(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
