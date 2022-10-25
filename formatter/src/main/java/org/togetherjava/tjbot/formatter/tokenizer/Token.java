package org.togetherjava.tjbot.formatter.tokenizer;

import java.util.Set;

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
    private static final Set<TokenType> DEBUG_SHOW_CONTENT_TYPES =
            Set.of(TokenType.IDENTIFIER, TokenType.UNKNOWN, TokenType.STRING, TokenType.COMMENT);

    @Override
    public String toString() {
        // For some types it helps debugging to also show the content
        String contentText =
                DEBUG_SHOW_CONTENT_TYPES.contains(type) ? "(%s)".formatted(content) : "";
        return type.name() + contentText;
    }
}
