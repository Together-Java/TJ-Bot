package org.togetherjava.tjbot.formatter.tokenizer;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to match tokens.
 */
final class Matching {
    private static final String CONTENT_GROUP = "content";

    private Matching() {
        throw new UnsupportedOperationException("Utility class, no implementation");
    }

    /**
     * Checks whether the given text starts with a string matching the pattern.
     * <p>
     * For example {@code matchesPattern(Pattern.compile("\\d+"), "12 foo")} would match and return
     * {@code "12"}.
     *
     * @param pattern the pattern to match with
     * @param text the text to match against
     * @return the text matched by the pattern, if any
     */
    static Optional<String> matchesPattern(Pattern pattern, CharSequence text) {
        Matcher matcher = patchPattern(pattern).matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String content = matcher.group(CONTENT_GROUP);
        return Optional.of(content);
    }

    private static Pattern patchPattern(Pattern pattern) {
        // ^ to prevent matching somewhere in the middle of the given text, e.g.,
        // "int x" should match for "int", not for "x".
        // Patterns need the named group to retrieve the content
        String patternText = "^(?<%s>%s)".formatted(CONTENT_GROUP, pattern.pattern());

        return Pattern.compile(patternText, pattern.flags());
    }

    /**
     * Checks whether the given text starts with the given symbol. Depending on the attribute, more
     * rules might apply.
     * <p>
     * For example {@code matchesSymbol("int", "int x = 5", KEYWORD)} would match and return
     * {@code "int"}.
     *
     * @param symbol the symbol to match with
     * @param text the text to match against
     * @param attribute the attribute of the symbol to match, for example {@code "class"} would be a
     *        {@link TokenType.Attribute#KEYWORD}
     * @return the given symbol, if it matches
     */
    static Optional<String> matchesSymbol(String symbol, CharSequence text,
            TokenType.Attribute attribute) {
        if (!startsWith(text, symbol)) {
            return Optional.empty();
        }

        if (attribute == TokenType.Attribute.KEYWORD && text.length() > symbol.length()) {
            // Must not be followed by letter
            char nextChar = text.charAt(symbol.length());
            if (Character.isLetter(nextChar)) {
                return Optional.empty();
            }
        }

        return Optional.of(symbol);
    }

    private static boolean startsWith(CharSequence text, String other) {
        // CharSequence unfortunately has no startsWith method, so we roll our own
        if (other.length() > text.length()) {
            return false;
        }

        // subSequence is only efficient on view-implementations, such as CharBuffer or Segment
        CharSequence candidate = text.subSequence(0, other.length());
        return other.contentEquals(candidate);
    }

    /**
     * Checks whether the given text starts with string, i.e. text contained in quotes
     * {@code "foo"}. Correctly handles escaped quotes, such as {@code "foo \"bar\" baz"}.
     * <p>
     * For example {@code matchesString("\"foo\"; int x = 5;")} would match and return
     * {@code "\"foo\""}.
     *
     * @param text the text to match against
     * @return the matched string, including starting and ending quotes, if any
     */
    static Optional<String> matchesString(CharSequence text) {
        if (text.length() < 2) {
            return Optional.empty();
        }
        if (text.charAt(0) != '"') {
            return Optional.empty();
        }

        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);

            // Strings end on unescaped ", i.e. not \"
            if (c == '"') {
                char previous = text.charAt(i - 1);
                if (previous != '\\') {
                    // Found the end of the string
                    String match = text.subSequence(0, i + 1).toString();
                    return Optional.of(match);
                }
            }
        }

        // String never ended
        return Optional.empty();
    }
}
