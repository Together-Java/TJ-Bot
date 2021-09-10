package org.togetherjava.formatter.tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lexer {
    private static final Pattern commentPatcherRegex = Pattern.compile("(/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/)");

    public List<Token> tokenize(String input) {
        return tokenize(Arrays.asList(patchComments(input).split("\n")));
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
                        String found = matcher.group(1);

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

    private String patchComments(String input) {
        Matcher matcher = commentPatcherRegex.matcher(input);

        while (matcher.find()) {
            String s = matcher.group();

            input = input.replace(s, s.replace("\n", " "));
        }

        return input;
    }
}
