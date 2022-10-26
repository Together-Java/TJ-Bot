package org.togetherjava.tjbot.formatter.tokenizer;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tokenizer that turns code into a list of tokens.
 */
public final class Lexer {
    /**
     * Tokenizes the given code into its individual tokens.
     *
     * @param code code to tokenize
     * @return the tokens the code consists of
     */
    public List<Token> tokenize(CharSequence code) {
        if (code.isEmpty()) {
            return List.of();
        }

        List<Token> tokens = new ArrayList<>();
        CharBuffer remainingCode = CharBuffer.wrap(code);

        while (!remainingCode.isEmpty()) {
            Token token = nextToken(remainingCode);
            tokens.add(token);

            advancePosition(remainingCode, token.content().length());
        }

        return tokens;
    }

    private Token nextToken(CharSequence content) {
        // Try all token types in order, take the first match
        return Stream.of(TokenType.getAllInMatchOrder())
            .map(tokenType -> tokenType.matches(content))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow();
    }

    private static void advancePosition(CharBuffer charBuffer, int advanceBy) {
        charBuffer.position(charBuffer.position() + advanceBy);
    }
}
