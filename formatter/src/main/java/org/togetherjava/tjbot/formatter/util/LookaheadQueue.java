package org.togetherjava.tjbot.formatter.util;

import java.util.Queue;

public interface LookaheadQueue<E> extends Queue<E> {
    /**
     * Peeks into the "future", peek(0) would be the equivalent to peek()
     */
    E peek(int n);
}
