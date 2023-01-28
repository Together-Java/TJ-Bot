package org.togetherjava.tjbot.features.utils;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StringDistancesTest {

    @Test
    void closeMatches() {
        record TestCase(String name, Collection<String> expectedSuggestions, String prefix,
                Collection<String> candidates, int limit) {
        }

        List<String> candidates = List.of("c", "c#", "c++", "emacs", "foo", "hello", "java", "js",
                "key", "nvim", "py", "tag", "taz", "vi", "vim");
        final int MAX_MATCHES = 5;

        List<TestCase> tests = List.of(
                new TestCase("no_tags", List.of(), "foo", List.of(), MAX_MATCHES),
                new TestCase("no_prefix", List.of("c", "c#", "c++", "emacs", "foo"), "", candidates,
                        MAX_MATCHES),
                new TestCase("both_empty", List.of(), "", List.of(), MAX_MATCHES),
                new TestCase("withPrefix0", List.of("vi", "vim"), "v", candidates, MAX_MATCHES),
                new TestCase("withPrefix1", List.of("java", "js"), "j", candidates, MAX_MATCHES),
                new TestCase("withPrefix2", List.of("c", "c#", "c++"), "c", candidates,
                        MAX_MATCHES));

        for (TestCase test : tests) {
            assertEquals(test.expectedSuggestions,
                    StringDistances.closeMatches(test.prefix, test.candidates, test.limit),
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
