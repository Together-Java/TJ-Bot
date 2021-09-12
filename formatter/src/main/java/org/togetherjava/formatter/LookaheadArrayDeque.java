package org.togetherjava.formatter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A {@link LookaheadQueue} implementation that is based on an {@link ArrayDeque}
 *
 * @author illuminator3
 */
public class LookaheadArrayDeque<E> extends ArrayDeque<E> implements LookaheadQueue<E> {
    public LookaheadArrayDeque() {
        super();
    }

    public LookaheadArrayDeque(int numElements) {
        super(numElements);
    }

    public LookaheadArrayDeque(Collection<? extends E> c) {
        super(c);
    }

    /**
     * {@inheritDoc}
     *
     * @author illuminator3
     */
    @Override
    public E peek(int n) {
        List<E> tmp = new ArrayList<>();

        while (n != 0 && !isEmpty()) {
            tmp.add(0, remove());

            n--;
        }

        E result = peek();

        for (E element : tmp) {
            addFirst(element);
        }

        return result;
    }
}
