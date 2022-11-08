package org.togetherjava.tjbot.formatter.formatting;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FormatterRulesTest {

    private static Stream<Arguments> providePatchMultiLineCommentTests() {
        return Stream.of(Arguments.of("basic", """
                /*
                         * Foo
                * Bar
                       */""", "", """
                /*
                 * Foo
                 * Bar
                 */"""), Arguments.of("single line", "/* Foo */", "", "/* Foo */"),
                Arguments.of("empty", "", "", ""),
                Arguments.of("empty comment", "/**/", "", "/**/"),
                Arguments.of("compact two lines", """
                        /*Foo
                        Bar*/""", "", """
                        /*Foo
                         Bar*/"""), Arguments.of("with indents", """
                        /*
                         * Foo
                         * Bar
                         */""", "  ", """
                        /*
                           * Foo
                           * Bar
                           */"""));
    }

    @ParameterizedTest
    @MethodSource("providePatchMultiLineCommentTests")
    void patchMultiLineComment(String testName, String comment, String indent,
            String expectedPatchedComment) {
        String actualPatchedComment = FormatterRules.patchMultiLineComment(comment, indent);

        assertEquals(expectedPatchedComment, actualPatchedComment, testName);
    }
}
