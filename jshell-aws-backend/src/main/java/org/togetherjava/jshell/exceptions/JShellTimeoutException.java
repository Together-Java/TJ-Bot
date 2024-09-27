package org.togetherjava.jshell.exceptions;

/**
 * An exception that covers when JShell times out.
 *
 * @author Suraj Kumar
 */
public class JShellTimeoutException extends RuntimeException {

    /** Constructs a JShellTimeoutException with a given message */
    public JShellTimeoutException(String message) {
        super(message);
    }
}
