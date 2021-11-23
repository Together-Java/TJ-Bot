package org.togetherjava.tjbot.commands.tags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class TagManageCommandTest {

    @Test
    void preserveLeadingSpaces() {
        String before = """
                hello  world
                 hello world\s
                  hello world \s
                   hello   world \s
                    hello     world  \s
                      hello world    \s
                          hello world   \s""";
        String after = """
                hello  world
                 hello world\s
                \u2800hello world \s
                \u2800 hello   world \s
                \u2800\u2800hello     world  \s
                \u2800\u2800\u2800hello world    \s
                \u2800\u2800\u2800\u2800\u2800hello world   \s""";

        assertEquals(after, TagManageCommand.preserveLeadingSpaces(before));
        assertEquals(before, TagManageCommand.undoPreserveLeadingSpaces(after));
    }
}
