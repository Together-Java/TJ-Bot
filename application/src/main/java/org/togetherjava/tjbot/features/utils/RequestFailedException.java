package org.togetherjava.tjbot.features.utils;

/**
 * Happens when an HTTP request has failed, contains an HTTP status code. Is the checked version of
 * {@link UncheckedRequestFailedException}.
 */
public class RequestFailedException extends Exception {
    private final int status;

    /**
     * Creates a RequestFailedException from an unchecked one.
     * 
     * @param ex the UncheckedRequestFailedException
     */
    public RequestFailedException(UncheckedRequestFailedException ex) {
        super(ex.getMessage());
        this.status = ex.getStatus();
    }

    /**
     * Creates a RequestFailedException from a message and a HTTP status
     * 
     * @param message the message
     * @param status the http status
     */
    public RequestFailedException(String message, int status) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status.
     * 
     * @return the HTTP status
     */
    public int getStatus() {
        return status;
    }
}
