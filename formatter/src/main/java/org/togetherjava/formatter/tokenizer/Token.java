package org.togetherjava.formatter.tokenizer;

import java.util.List;

public record Token(String content, TokenType type) {
    private static final List<TokenType> displayTypes =
            List.of(TokenType.IDENTIFIER, TokenType.UNKNOWN, TokenType.STRING, TokenType.COMMENT);

    @Override
    public String toString() {
        return type().name() + displayMe();
    }

    private String displayMe() {
        if (displayTypes.contains(type())) {
            return "(" + content() + ")";
        }

        return "";
    }
}
