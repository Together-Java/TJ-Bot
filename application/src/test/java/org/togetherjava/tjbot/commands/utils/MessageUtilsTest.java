package org.togetherjava.tjbot.commands.utils;

import org.junit.jupiter.api.Test;

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
}
