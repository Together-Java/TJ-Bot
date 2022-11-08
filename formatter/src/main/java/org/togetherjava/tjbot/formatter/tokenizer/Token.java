package org.togetherjava.tjbot.formatter.tokenizer;

/**
 * Single token of a code, for example an opening-brace.
 * <p>
 * As example, the code {@code int x = "foo";} is split into tokens:
 * <ul>
 * <li>("int", INT)</li>
 * <li>("x", IDENTIFIER)</li>
 * <li>("=", ASSIGN)</li>
 * <li>("\"foo\"", STRING)</li>
 * <li>(";", SEMICOLON)</li>
 * </ul>
 *
 * @param content the actual text contained in the token, e.g., an identifier like {@code x}
 * @param type the type of the token, e.g., IDENTIFIER
 */
public record Token(String content, TokenType type) {
    @Override
    public String toString() {
        return "%s(%s)".formatted(type.name(), content);
    }
}
