package org.togetherjava.tjbot.features.system;

import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.features.componentids.ComponentId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ComponentIdTest {
    @Test
    void getUserInteractorName() {
        String userInteractorName = "foo";
        assertEquals(userInteractorName,
                new ComponentId(userInteractorName, List.of()).userInteractorName());
    }

    @Test
    void getElements() {
        List<String> elements = List.of();
        assertEquals(elements, new ComponentId("foo", elements).elements());

        elements = List.of("foo", "bar", "baz");
        assertEquals(elements, new ComponentId("foo", elements).elements());
    }
}
