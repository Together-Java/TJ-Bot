package org.togetherjava.tjbot.commands.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StringDistancesTest {

    @Test
    void editDistance() {
        record TestCase(String name, int expectedDistance, String source, String destination) {
        }
        List<TestCase> tests = List.of(new TestCase("identity", 0, "-", "-"),
                new TestCase("empty_identity", 0, "", ""), new TestCase("empty_remove", 1, "a", ""),
                new TestCase("empty_add", 1, "", "a"), new TestCase("basic", 4, "bloed", "doof"),
                new TestCase("basic_all", 3, "---", "abc"),
                new TestCase("prefix", 4, "abc", "abcdefg"),
                new TestCase("small_diff", 5, "acc", "abcdefg"),
                new TestCase("swap", 5, "acb", "abcdefg"));

        for (TestCase test : tests) {
            assertEquals(test.expectedDistance,
                    StringDistances.editDistance(test.source, test.destination),
                    "Test '%s' failed".formatted(test.name));
        }
    }

    @Test
    void prefixEditDistance() {
        record TestCase(String name, int expectedDistance, String source, String destination) {
        }
        List<TestCase> tests = List.of(new TestCase("identity", 0, "-", "-"),
                new TestCase("empty_identity", 0, "", ""), new TestCase("empty_remove", 1, "a", ""),
                new TestCase("empty_add", 0, "", "a"), new TestCase("basic", 4, "bloed", "doof"),
                new TestCase("basic_all", 3, "---", "abc"),
                new TestCase("prefix", 0, "abc", "abcdefg"),
                new TestCase("small_diff", 1, "acc", "abcdefg"),
                new TestCase("swap", 1, "acb", "abcdefg"));

        for (TestCase test : tests) {
            assertEquals(test.expectedDistance,
                    StringDistances.prefixEditDistance(test.source, test.destination),
                    "Test '%s' failed".formatted(test.name));
        }
    }
}
