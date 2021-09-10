package org.togetherjava.formatter.tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer that can turn a list of strings (or a string) into a list of tokens
 *
 * @author illuminator3
 */
public class Lexer {
    private static final Pattern commentPatcherRegex = Pattern.compile("(/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/)");

    /**
     * Tokenizes the given input by tokenizing each line individually (splitting by \n)
     *
     * @param input input to tokenize
     * @return resulting tokens
     * @author illuminator3
     */
    public List<Token> tokenize(String input) {
        return tokenize(Arrays.asList(patchComments(input).split("\n")));
    }

    /**
     * Tokenizes the given input line by line
     *
     * @param lines input to tokenize
     * @return resulting tokens
     * @author illuminator3
     */
    public List<Token> tokenize(List<String> lines) {
        return lines.stream()
                    .map(this::tokenizeLine)
                    .flatMap(List::stream)
                    .toList();
    }

    /**
     * Tokenizes a single line using regex
     *
     * @param line input to tokenize
     * @return resulting tokens
     * @author illuminator3
     */
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

    /**
     * Replaces multi-line comments in a given string by single-line comments
     *
     * @param input input to patch
     * @return resulting string
     * @author illuminator3
     */
    private String patchComments(String input) { // fix this, you shouldn't need this!
        Matcher matcher = commentPatcherRegex.matcher(input);

        while (matcher.find()) {
            String s = matcher.group();

            input = input.replace(s, s.replace("\n", " "));
        }

        return input;
    }
}
