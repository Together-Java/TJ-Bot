package org.togetherjava.tjbot.features.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MessageUtilsTest {

    @Test
    void escapeMarkdown() {
        List<TestCaseEscape> tests = List.of(new TestCaseEscape("empty", "", ""),
                new TestCaseEscape("no markdown", "hello world", "hello world"),
                new TestCaseEscape(
                        "basic markdown", "\\*\\*hello\\*\\* \\_world\\_", "**hello** _world_"),
                new TestCaseEscape("code block", """
                        \\`\\`\\`java
                        int x = 5;
                        \\`\\`\\`
                        """, """
                        ```java
                        int x = 5;
                        ```
                        """),
                new TestCaseEscape("escape simple", "hello\\\\\\\\world\\\\\\\\test",
                        "hello\\\\world\\\\test"),
                new TestCaseEscape("escape complex", """
                        Hello\\\\\\\\world
                        \\`\\`\\`java
                        Hello\\\\\\\\
                        world
                        \\`\\`\\`
                        test out this
                        \\`\\`\\`java
                        "Hello \\\\" World\\\\\\\\\\\\"" haha
                        \\`\\`\\`
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
                        """),
                new TestCaseEscape("escape real example",
                        """
                                Graph traversal can be accomplished easily using \\*\\*BFS\\*\\* or \\*\\*DFS\\*\\*. The algorithms only differ in the order in which nodes are visited: https://i.imgur.com/n9WrkQG.png

                                The code to accomplish them is identical and only differs in the behavior of the \\`Queue\\` they are based on. \\*\\*BFS\\*\\* uses a \\*\\*FIFO\\*\\*-queue and \\*\\*DFS\\*\\* a \\*\\*LIFO\\*\\*-queue.
                                \\`\\`\\`java
                                Queue<Node> nodesToProcess = ... // depending on BFS or DFS
                                nodesToProcess.add(rootNode); // add all starting nodes

                                Set<Queue> visitedNodes = new HashSet<>();
                                while (!nodesToProcess.isEmpty()) {
                                  // Settle node
                                  Node currentNode = visitedNodes.poll();
                                  if (!visitedNodes.add(currentNode)) {
                                    continue; // Already visited before
                                  }

                                  // Do something with the node
                                  System.out.println(currentNode); // Replace by whatever you need

                                  // Relax all outgoing edges
                                  for (Node neighbor : currentNode.getNeighbors()) {
                                    nodesToProcess.add(neighbor);
                                  }
                                }
                                \\`\\`\\`
                                To get \\*\\*BFS\\*\\*, use a \\*\\*FIFO\\*\\*-queue:
                                \\`\\`\\`java
                                Queue<Node> nodesToProcess = new ArrayDeque<>();
                                \\`\\`\\`
                                And for \\*\\*DFS\\*\\* a \\*\\*LIFO\\*\\*-queue:
                                \\`\\`\\`java
                                Queue<Node> nodesToProcess = Collections.asLifoQueue(new ArrayDeque<>());
                                \\`\\`\\`
                                That's all, very simple to setup and use.

                                For directed graphs relax all \\*\\*outgoing edges\\*\\*.
                                For \\*\\*tree\\*\\*s the \\`visitedNodes\\` logic can be dropped, since each node can only have maximally one parent, simplifying the algorithm to just:
                                \\`\\`\\`java
                                Queue<Node> nodesToProcess = ... // depending on BFS or DFS
                                nodesToProcess.add(rootNode); // add all starting nodes

                                while (!nodesToProcess.isEmpty()) {
                                  // Settle node
                                  Node currentNode = visitedNodes.poll();

                                  // Do something with the node
                                  System.out.println(currentNode); // Replace by whatever you need

                                  // Relax all outgoing edges
                                  for (Node child : currentNode.getChildren()) {
                                    nodesToProcess.add(child);
                                  }
                                }
                                \\`\\`\\`
                                """,
                        """
                                Graph traversal can be accomplished easily using **BFS** or **DFS**. The algorithms only differ in the order in which nodes are visited: https://i.imgur.com/n9WrkQG.png

                                The code to accomplish them is identical and only differs in the behavior of the `Queue` they are based on. **BFS** uses a **FIFO**-queue and **DFS** a **LIFO**-queue.
                                ```java
                                Queue<Node> nodesToProcess = ... // depending on BFS or DFS
                                nodesToProcess.add(rootNode); // add all starting nodes

                                Set<Queue> visitedNodes = new HashSet<>();
                                while (!nodesToProcess.isEmpty()) {
                                  // Settle node
                                  Node currentNode = visitedNodes.poll();
                                  if (!visitedNodes.add(currentNode)) {
                                    continue; // Already visited before
                                  }

                                  // Do something with the node
                                  System.out.println(currentNode); // Replace by whatever you need

                                  // Relax all outgoing edges
                                  for (Node neighbor : currentNode.getNeighbors()) {
                                    nodesToProcess.add(neighbor);
                                  }
                                }
                                ```
                                To get **BFS**, use a **FIFO**-queue:
                                ```java
                                Queue<Node> nodesToProcess = new ArrayDeque<>();
                                ```
                                And for **DFS** a **LIFO**-queue:
                                ```java
                                Queue<Node> nodesToProcess = Collections.asLifoQueue(new ArrayDeque<>());
                                ```
                                That's all, very simple to setup and use.

                                For directed graphs relax all **outgoing edges**.
                                For **tree**s the `visitedNodes` logic can be dropped, since each node can only have maximally one parent, simplifying the algorithm to just:
                                ```java
                                Queue<Node> nodesToProcess = ... // depending on BFS or DFS
                                nodesToProcess.add(rootNode); // add all starting nodes

                                while (!nodesToProcess.isEmpty()) {
                                  // Settle node
                                  Node currentNode = visitedNodes.poll();

                                  // Do something with the node
                                  System.out.println(currentNode); // Replace by whatever you need

                                  // Relax all outgoing edges
                                  for (Node child : currentNode.getChildren()) {
                                    nodesToProcess.add(child);
                                  }
                                }
                                ```
                                """));

        for (TestCaseEscape test : tests) {
            assertEquals(test.escapedMessage(), MessageUtils.escapeMarkdown(test.originalMessage()),
                    "Test failed: " + test.testName());
        }
    }

    private record TestCaseEscape(String testName, String escapedMessage, String originalMessage) {
    }

    @Test
    void abbreviate() {
        List<TestCaseAbbreviate> tests =
                List.of(new TestCaseAbbreviate("base case", "hello...", "hello world", 8),
                        new TestCaseAbbreviate("long enough", "hello world", "hello world", 15),
                        new TestCaseAbbreviate("exact size", "hello world", "hello world", 11),
                        new TestCaseAbbreviate("very small limit", "he", "hello world", 2),
                        new TestCaseAbbreviate("empty input", "", "", 0),
                        new TestCaseAbbreviate("zero limit", "", "hello world", 0),
                        new TestCaseAbbreviate("small limit", "h...", "hello world", 4));

        for (TestCaseAbbreviate test : tests) {
            assertEquals(test.abbreviatedMessage(),
                    MessageUtils.abbreviate(test.originalMessage(), test.limit()),
                    "Test failed: " + test.testName());
        }
    }

    private record TestCaseAbbreviate(String testName, String abbreviatedMessage,
            String originalMessage, int limit) {
    }

    private static List<Arguments> provideExtractCodeTests() {
        List<Arguments> tests = new ArrayList<>();
        tests.add(createExtractCodeArgumentsFor("basic", """
                Foo
                %s
                Bar""", "java", """
                public class Foo {
                  public static void main(String[] args) {
                    System.out.println("Hello");
                  }
                }"""));

        tests.add(createExtractCodeArgumentsFor("code only", "%s", "java", """
                int x = 5;"""));

        tests.add(Arguments.of("fence ends on code line", """
                ```java
                int x = 5;```""", new CodeFence("java", "int x = 5;")));

        tests.add(createExtractCodeArgumentsFor("other language", "Foo %s Bar", "kotlin", """
                fun main() {
                  println("Hello, World!")
                }"""));

        tests.add(createExtractCodeArgumentsFor("no language", "Foo %s Bar", null, "foo=bar"));

        tests.add(Arguments.of("no language single line no spaces", "```int```",
                new CodeFence(null, "int")));

        tests.add(createExtractCodeArgumentsFor("multiple fences", """
                Foo
                %s
                Bar
                ```java
                int x = 5;
                ```""", "java", "System.out.println(10);"));

        tests.add(Arguments.of("no code", "foo", null));
        tests.add(Arguments.of("empty", "", null));

        tests.add(Arguments.of("unclosed code block", """
                Foo
                ```java
                int x = 5;""", null));

        tests.add(Arguments.of("almost closed code block", """
                ```java
                int x = 5;
                ``""", null));

        tests.add(Arguments.of("small code fence", "Foo `int x = 5` Bar", null));

        tests.add(Arguments.of("six backticks", """
                ``````test
                foo```""", new CodeFence("```test", "foo")));

        tests.add(Arguments.of("single line", "```java test```", new CodeFence(null, "java test")));

        tests.add(Arguments.of("space in language", """
                ```java test
                test```
                """, new CodeFence(null, """
                java test
                test""")));

        return tests;
    }

    private static Arguments createExtractCodeArgumentsFor(String testName, String textTemplate,
            @Nullable String language, String code) {
        String codeInFence = """
                ```%s
                %s
                ```""".formatted(language == null ? "" : language, code);

        String text = textTemplate.formatted(codeInFence);
        return Arguments.of(testName, text, new CodeFence(language, code));
    }

    @ParameterizedTest
    @MethodSource("provideExtractCodeTests")
    void extractCode(String testName, String text, @Nullable CodeFence expectedCodeFence) {
        CodeFence actualCodeFence = MessageUtils.extractCode(text).orElse(null);

        assertEquals(expectedCodeFence, actualCodeFence, testName);
    }
}
