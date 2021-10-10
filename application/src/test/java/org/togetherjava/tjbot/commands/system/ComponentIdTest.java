package org.togetherjava.tjbot.commands.system;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ComponentIdTest {
    private static final String NAME = "foo";
    @SuppressWarnings("StaticCollection")
    private static final List<String> ELEMENTS = List.of("foo", "bar", "baz");
    private static final int UNIQUE_ID_ITERATIONS = 20;

    static ComponentId createComponentId() {
        return new ComponentId(NAME, ELEMENTS);
    }

    @Test
    void getCommandName() {
        assertEquals(NAME, createComponentId().getCommandName());
    }

    @Test
    void getId() {
        Collection<Integer> ids = new HashSet<>();
        for (int i = 0; i < UNIQUE_ID_ITERATIONS; i++) {
            int id = createComponentId().getId();
            assertFalse(ids.contains(id), "id generator is supposed to create unique IDs");
            ids.add(id);
        }
    }

    @Test
    void getElements() {
        assertEquals(ELEMENTS, createComponentId().getElements());
    }
}
