package org.togetherjava.formatter.tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class Lexer {
    private static final Lexer instance = new Lexer();

    public static Lexer getInstance() {
        return instance;
    }

    public List<Token> tokenize(String input) {
        return tokenize(Arrays.asList(input.split("\n")));
    }

    public List<Token> tokenize(List<String> lines) {
        return lines.stream()
                    .map(this::tokenizeLine)
                    .flatMap(List::stream)
                    .toList();
    }

    private List<Token> tokenizeLine(String line) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        String content;

        while (!(content = line.substring(index)).isEmpty()) {
            tokenCheck: {
                for (TokenType token : TokenType.values()) {
                    Matcher matcher = token.getRegex().matcher(content);

                    if (matcher.find()) {
                        String found = matcher.group();

                        tokens.add(new Token(found, token));
                        index += found.length();

                        break tokenCheck;
                    }
                }

                throw new TokenizationException("Token not found for '" + content + "'");
            }
        }

        return tokens;
    }
}
