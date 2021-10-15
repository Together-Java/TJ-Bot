package org.togetherjava.tjbot.commands.system;

import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.commands.componentids.ComponentId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ComponentIdsTest {

    private record TestScenario(String testName, String commandName, List<String> elements) {
    }

    @Test
    void generateConsistentWithParse() {
        /*
         * List<TestScenario> tests = List.of(new TestScenario("base", "foo", List.of("foo", "bar",
         * "baz")), new TestScenario("emptyElements", "foo", List.of()), new
         * TestScenario("emptyCommandName", "", List.of("foo", "bar", "baz")), new
         * TestScenario("emptyAll", "", List.of()), new TestScenario("longElements", "foo",
         * List.of("foo", "bar", "baz", "lorem ipsum lorem ipsum lorem ipsum lorem ipsum",
         * "lorem ipsum, lorem ipsum, lorem ipsum, lorem ipsum")), new
         * TestScenario("complexCharacters", "a.-,?!\"'\"\"c'd\"$$e",
         * List.of("a.-,?!\"'\"\"c'd\"$$e", "a.-,?!\"'\"\"c'd\"$$e")));
         * 
         * for (TestScenario test : tests) { String idText = assertDoesNotThrow( () ->
         * ComponentIds.generate(test.commandName(), test.elements()), "Test failed to generate: " +
         * test.testName()); ComponentId id = assertDoesNotThrow(() -> ComponentIds.parse(idText),
         * "Test failed to parse: " + test.testName());
         * 
         * assertEquals(test.commandName(), id.getCommandName(), "Test failed: " + test.testName());
         * assertEquals(test.elements(), id.getElements(), "Test failed: " + test.testName()); }
         */
    }
}
