package org.togetherjava.tjbot.db.util;

/**
 * {@link java.util.function.Function} extension that can possibly throw an exception.
 * <p>
 * Represents a function that accepts a single input argument, returns a result and could possibly
 * throw an exception.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the function's return value
 * @param <E> the type of the exception
 */
@FunctionalInterface
public interface CheckedFunction<T, R, E extends Throwable> {

    /**
     * Performs this operation on the given argument.
     *
     * @param input the input argument
     * @return R on successful evaluation
     * @throws E an exception if any occurs during the execution of the operation
     */
    R accept(T input) throws E;

}
