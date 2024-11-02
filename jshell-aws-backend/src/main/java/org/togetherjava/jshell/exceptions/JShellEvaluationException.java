package org.togetherjava.jshell.exceptions;

/**
 * An exception that covers when code snippet evaluation fails.
 *
 * @author Suraj Kumar
 */
public class JShellEvaluationException extends RuntimeException {

    /** Constructs a JShellEvaluationException with a given message */
    public JShellEvaluationException(String message) {
        super(message);
    }
}
