package org.togetherjava.tjbot.commands.utils;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StringDistancesTest {

    @Test
    void autoCompleteSuggestions() {
        record TestCase(String name, Collection<String> expectedSuggestions, String prefix,
                Collection<String> candidates, double errorMargin) {
        }

        List<TestCase> tests =
                List.of(new TestCase("empty_candidates", List.of(), "prefix", List.of(), 0),
                        new TestCase("empty_prefix", List.of("one", "three", "two"), "",
                                List.of("one", "two", "three"), 0),
                        new TestCase("all_empty", List.of(), "", List.of(), 1),
                        new TestCase("max_error", List.of("aa"), "a", List.of("json", "one", "aa", "two", "++"), 1),
                        new TestCase("real_test", List.of("j", "java", "js", "one"), "jo",
                                List.of("java", "xj", "bs", "one", "yes", "js", "a", "j"), 0.5));

        for (TestCase test : tests) {
            assertEquals(
                    test.expectedSuggestions, StringDistances.autocompleteSuggestions(test.prefix,
                            test.candidates, test.errorMargin),
                    "Test '%s' failed".formatted(test.name));
        }
    }

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
