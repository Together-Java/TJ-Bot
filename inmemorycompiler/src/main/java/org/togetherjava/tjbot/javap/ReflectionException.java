package org.togetherjava.tjbot.javap;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown when an exception that is caused by things relating to reflection occurs. Typically, thrown by {@link Javap} (which uses A LOT of reflection).
 */
class ReflectionException extends RuntimeException {
    public ReflectionException(@NotNull String message, @NotNull Exception cause) {
        super(message, cause);
    }
}
