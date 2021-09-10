package org.togetherjava.tjbot.util;

/**
 * Represents a function that accepts a single input argument, returns a result and could possibly
 * throw an exception.
 *
 * @param <T> the type of the input to the function
 * @param <E> the type of the exception
 */
@FunctionalInterface
public interface CheckedConsumer<T, E extends Throwable> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     * @throws E an exception if any occurs during the execution of the operation
     */
    void accept(T t) throws E;

}

