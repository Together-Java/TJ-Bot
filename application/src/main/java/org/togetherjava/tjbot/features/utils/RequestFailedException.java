package org.togetherjava.tjbot.features.utils;

public class RequestFailedException extends Exception {
    private final int status;

    public RequestFailedException(UncheckedRequestFailedException ex) {
        super(ex.getMessage());
        this.status = ex.getStatus();
    }

    public RequestFailedException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
