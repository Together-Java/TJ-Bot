package org.togetherjava.formatter;

import java.util.function.Predicate;

public interface SkippableLookaheadQueue<E> extends LookaheadQueue<E> {
    E peek(int n, Predicate<E> skip);
}
