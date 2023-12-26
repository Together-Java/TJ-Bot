package org.togetherjava.tjbot.db.util;

/**
 * {@link java.util.function.Consumer} extension that can possibly throw an exception.
 * <p>
 * Represents a function that accepts a single input argument, returns no result and could possibly
 * throw an exception.
 *
 * @param <T> the type of the input to the consumer
 * @param <E> the type of the exception
 */
@FunctionalInterface
public interface CheckedConsumer<T, E extends Throwable> {

    /**
     * Performs this operation on the given argument.
     *
     * @param input the input argument
     * @throws E an exception if any occurs during the execution of the operation
     */
    void accept(T input) throws E;

}
