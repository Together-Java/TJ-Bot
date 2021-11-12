package org.togetherjava.tjbot.commands.system;

import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.commands.componentids.ComponentId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ComponentIdTest {
    @Test
    void getCommandName() {
        String commandName = "foo";
        assertEquals(commandName, new ComponentId(commandName, List.of()).commandName());
    }

    @Test
    void getElements() {
        List<String> elements = List.of();
        assertEquals(elements, new ComponentId("foo", elements).elements());

        elements = List.of("foo", "bar", "baz");
        assertEquals(elements, new ComponentId("foo", elements).elements());
    }
}
