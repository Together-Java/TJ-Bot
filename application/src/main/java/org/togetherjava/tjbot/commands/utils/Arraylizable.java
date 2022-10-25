package org.togetherjava.tjbot.commands.utils;

/**
 * An interface with just a {@link Arraylizable#toArray()} method. This is helpful for passing
 * classes that provide component arguments.
 *
 * @param <T> The type of the array. Usually {@link String} for component arguments
 */
public interface Arraylizable<T> {
    /**
     * Converts this class to an array of type {@link T}
     *
     * @return The class converted to an array of type {@link T}
     */
    T[] toArray();
}
