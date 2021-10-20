package org.togetherjava.tjbot.commands.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MessageUtilsTest {

    @Test
    void escapeMarkdown() {
        List<TestCase> tests = List.of(new TestCase("empty", "", ""),
                new TestCase("no markdown", "hello world", "hello world"),
                new TestCase("basic markdown", "\\*\\*hello\\*\\* \\_world\\_",
                        "**hello** _world_"),
                new TestCase("code block", """
                        \\```java
                        int x = 5;
                        \\```
                        """, """
                        ```java
                        int x = 5;
                        ```
                        """), new TestCase("escape simple", "hello\\\\\\\\world\\\\\\\\test",
                        "hello\\\\world\\\\test"),
                new TestCase("escape complex", """
                        Hello\\\\\\\\world
                        \\```java
                        Hello\\\\\\\\
                        world
                        \\```
                        test out this
                        \\```java
                        "Hello \\\\" World\\\\\\\\\\\\"" haha
                        \\```
                        """, """
                        Hello\\\\world
                        ```java
                        Hello\\\\
                        world
                        ```
                        test out this
                        ```java
                        "Hello \\" World\\\\\\"" haha
                        ```
                        """));

        for (TestCase test : tests) {
            assertEquals(test.escapedMessage(), MessageUtils.escapeMarkdown(test.originalMessage()),
                    "Test failed: " + test.testName());
        }
    }

    private record TestCase(String testName, String escapedMessage, String originalMessage) {
    }
}
