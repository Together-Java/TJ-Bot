package org.togetherjava.tjbot.formatter.tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tokenizer that can turn a list of strings (or a string) into a list of tokens
 */
public class Lexer {
    /**
     * Tokenizes the given input by tokenizing each line individually (splitting by \n)
     *
     * @param input input to tokenize
     * @return resulting tokens
     */
    public List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        String content;

        // TODO Performance problem, use rolling window instead
        while (!(content = input.substring(index)).isEmpty()) {
            Token token = findToken(content);

            index += token.content().length();

            tokens.add(token);
        }

        // FIXME Replace by some nice trace logging
        tokens.stream().forEach(System.out::println);

        return tokens;
    }

    private Token findToken(String content) {
        for (TokenType type : TokenType.values()) {
            Optional<Token> maybeToken = type.matches(content);

            if (maybeToken.isPresent()) {
                return maybeToken.orElseThrow();
            }
        }

        throw new TokenizationException("Token not found for '" + content + "'");
    }
}
