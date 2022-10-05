package org.togetherjava.tjbot.javap;

/**
 * Thrown when an exception that is caused by things relating to reflection occurs. Typically,
 * thrown by {@link Javap} (which uses A LOT of reflection).
 */
class ReflectionException extends RuntimeException {
    public ReflectionException(String message, Exception cause) {
        super(message, cause);
    }
}
