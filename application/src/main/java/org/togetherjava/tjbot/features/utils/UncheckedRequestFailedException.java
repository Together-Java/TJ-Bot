package org.togetherjava.tjbot.features.utils;

/**
 * Internal exception when an HTTP request has failed, contains an HTTP status code. Is the
 * unchecked version of {@link RequestFailedException}.
 */
public class UncheckedRequestFailedException extends RuntimeException {
    private final int status;

    /**
     * Creates an UncheckedRequestFailedException from a message and a HTTP status
     * 
     * @param message the message
     * @param status the http status
     */
    public UncheckedRequestFailedException(String message, int status) {
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

    /**
     * Creates a checked RequestFailedException from this UncheckedRequestFailedException.
     * 
     * @return a checked RequestFailedException
     */
    public RequestFailedException toChecked() {
        return new RequestFailedException(this);
    }
}
