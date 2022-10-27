package org.togetherjava.tjbot.formatter.tokenizer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nullable;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MatchingTest {

    private static Stream<Arguments> provideMatchesPatternTests() {
        return Stream.of(Arguments.of("basic", "123foo", Pattern.compile("\\d+"), "123"),
                Arguments.of("does not start with pattern", "foo123", Pattern.compile("\\d+"),
                        null),
                Arguments.of("empty", "", Pattern.compile("\\d+"), null),
                Arguments.of("pattern with flags", """
                        1
                        2
                        3foo""", Pattern.compile(".+(?=foo)", Pattern.DOTALL), """
                        1
                        2
                        3"""));
    }

    @ParameterizedTest
    @MethodSource("provideMatchesPatternTests")
    void matchesPattern(String testName, String text, Pattern pattern,
            @Nullable String expectedMatch) {
        String actualMatch = Matching.matchesPattern(pattern, text).orElse(null);

        assertEquals(expectedMatch, actualMatch, testName);
    }

    private static Stream<Arguments> provideMatchesSymbolTests() {
        return Stream.of(Arguments.of("basic", "int x = 5;", "int", TokenType.Attribute.NONE, true),
                Arguments.of("does not start with symbol", "int x = 5;", "x",
                        TokenType.Attribute.NONE, false),
                Arguments.of("no symbol", "int x = 5;", "double", TokenType.Attribute.NONE, false),
                Arguments.of("empty", "", "int", TokenType.Attribute.NONE, false),
                Arguments.of("only symbol", "int", "int", TokenType.Attribute.NONE, true),
                Arguments.of("multiple symbols", "int int int", "int", TokenType.Attribute.NONE,
                        true),
                Arguments.of("keyword as identifier", "newValue = 5", "new",
                        TokenType.Attribute.KEYWORD, false),
                Arguments.of("keyword without space", "new=5", "new", TokenType.Attribute.KEYWORD,
                        true),
                Arguments.of("only keyword", "class", "class", TokenType.Attribute.KEYWORD, true));
    }

    @ParameterizedTest
    @MethodSource("provideMatchesSymbolTests")
    void matchesSymbol(String testName, String text, String symbol, TokenType.Attribute attribute,
            boolean expectMatch) {
        String expectedMatch = expectMatch ? symbol : null;

        String actualMatch = Matching.matchesSymbol(symbol, text, attribute).orElse(null);

        assertEquals(expectedMatch, actualMatch, testName);
    }

    private static Stream<Arguments> provideMatchesStringTests() {
        return Stream.of(Arguments.of("basic", "\"bar\" baz", "\"bar\""),
                Arguments.of("does not start with string", "foo \"bar\" baz", null),
                Arguments.of("no string", "foo bar", null), Arguments.of("empty", "", null),
                Arguments.of("only string", "\"foo\"", "\"foo\""), Arguments.of("multi line", """
                        "hello
                        world" bar""", "\"hello\nworld\""), Arguments.of("escaped", """
                        "foo \\"bar\\" baz" after
                        """, """
                        "foo \\"bar\\" baz\""""));
    }

    @ParameterizedTest
    @MethodSource("provideMatchesStringTests")
    void matchesString(String testName, String text, @Nullable String expectedMatch) {
        String actualMatch = Matching.matchesString(text).orElse(null);

        assertEquals(expectedMatch, actualMatch, testName);
    }
}
