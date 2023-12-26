package org.togetherjava.tjbot.features.utils;

/**
 * Happens when a connection has failed, or the URL was invalid.
 */
public class ConnectionFailedException extends Exception {

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to {@link #initCause}.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the
     *        {@link #getMessage()} method.
     */
    public ConnectionFailedException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * <p>
     * Note that the detail message associated with {@code cause} is <i>not</i> automatically
     * incorporated in this exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the
     *        {@link #getMessage()} method).
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()}
     *        method). (A {@code null} value is permitted, and indicates that the cause is
     *        nonexistent or unknown.)
     */
    public ConnectionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
