package org.togetherjava.formatter.tokenizer;

import java.util.List;

/**
 * Class representing a Token with a given content and a type
 *
 * @author illuminator3
 */
public record Token(String content, TokenType type) {
    private static final List<TokenType> displayTypes =
            List.of(TokenType.IDENTIFIER, TokenType.UNKNOWN, TokenType.STRING, TokenType.COMMENT);

    @Override
    public String toString() {
        return type().name() + displayMe();
    }

    /**
     * Returns a non-empty string if this token has something to display
     *
     * @return the displayed value
     * @author illuminator3
     */
    private String displayMe() {
        if (displayTypes.contains(type())) {
            return "(" + content() + ")";
        }

        return "";
    }
}
