package org.togetherjava.tjbot.util;

/**
 * Extension of {@link java.util.function.BiConsumer} but for 3 elements.
 * <p>
 * Represents an operation that accepts three input arguments and returns no result. This is the
 * three-arity specialization of {@link java.util.function.Consumer}. Unlike most other functional
 * interfaces, TriConsumer is expected to operate via side effects.
 *
 * @param <A> the type of the first argument to the operation
 * @param <B> the type of the second argument to the operation
 * @param <C> the type of the third argument to the operation
 */
@FunctionalInterface
public interface TriConsumer<A, B, C> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param first the first input argument
     * @param second the second input argument
     * @param third the third input argument
     */
    void accept(A first, B second, C third);
}
