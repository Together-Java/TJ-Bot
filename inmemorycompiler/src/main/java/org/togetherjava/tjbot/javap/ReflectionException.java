package org.togetherjava.tjbot.javap;

class ReflectionException extends RuntimeException {
    public ReflectionException(String message, Exception wraps) {
        super(message, wraps);
    }

    public ReflectionException(String message) {
        super(message);
    }

    public ReflectionException(Exception wraps) {
        super(wraps);
    }
}
