package org.togetherjava.tjbot.features.utils;

public class UncheckedRequestFailedException extends RuntimeException {
    private final int status;

    public UncheckedRequestFailedException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public RequestFailedException toChecked() {
        return new RequestFailedException(this);
    }
}
