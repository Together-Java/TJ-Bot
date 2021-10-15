package org.togetherjava.tjbot.formatter.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class SkippableLookaheadArrayDeque<E> extends LookaheadArrayDeque<E>
        implements SkippableLookaheadQueue<E> {
    public SkippableLookaheadArrayDeque() {}

    public SkippableLookaheadArrayDeque(int numElements) {
        super(numElements);
    }

    public SkippableLookaheadArrayDeque(Collection<? extends E> c) {
        super(c);
    }

    @Override
    public E peek(int n, Predicate<E> skip) {
        List<E> tmp = new ArrayList<>();

        while (n != 0 && !isEmpty()) {
            E current = remove();

            tmp.add(0, current);

            if (!skip.test(current)) {
                n--;
            }
        }

        E result = peek();

        for (E element : tmp) {
            addFirst(element);
        }

        return result;
    }
}
