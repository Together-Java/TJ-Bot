package org.togetherjava.tjbot.formatter.tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer that can turn a list of strings (or a string) into a list of tokens
 */
public class Lexer {
    /**
     * Regex to match multi-line Java comments (including Javadoc)
     */
    private static final Pattern commentPatcherRegex =
            Pattern.compile("(/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/)");

    /**
     * Tokenizes the given input by tokenizing each line individually (splitting by \n)
     *
     * @param input input to tokenize
     * @return resulting tokens
     */
    public List<Token> tokenize(String input) {
        return tokenize(Arrays.asList(patchComments(input).split("\n")));
    }

    /**
     * Tokenizes the given input line by line
     *
     * @param lines input to tokenize
     * @return resulting tokens
     */
    public List<Token> tokenize(List<String> lines) {
        return lines.stream().map(this::tokenizeLine).flatMap(List::stream).toList();
    }

    /**
     * Tokenizes a single line using regex
     *
     * @param line input to tokenize
     * @return resulting tokens
     */
    private List<Token> tokenizeLine(String line) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        String content;

        while (!(content = line.substring(index)).isEmpty()) {
            Token token = findToken(content);

            index += token.content().length();

            tokens.add(token);
        }

        return tokens;
    }

    private Token findToken(String content) {
        for (TokenType type : TokenType.values()) {
            Matcher matcher = type.getRegex().matcher(content);

            if (matcher.find()) {
                return new Token(matcher.group(1), type);
            }
        }

        throw new TokenizationException("Token not found for '" + content + "'");
    }

    /**
     * Replaces multi-line comments in a given string by single-line comments
     *
     * @param input input to patch
     * @return resulting string
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
