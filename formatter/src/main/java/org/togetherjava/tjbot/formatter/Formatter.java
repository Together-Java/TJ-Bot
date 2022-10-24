package org.togetherjava.tjbot.formatter;

import org.togetherjava.tjbot.formatter.tokenizer.Lexer;
import org.togetherjava.tjbot.formatter.tokenizer.Token;

import java.util.List;

/**
 * Formatter which can format a given string into a string which contains code blocks etc
 */
public class Formatter {
    /**
     * Formats the given tokens
     *
     * @param tokens tokens to format
     * @return resulting code
     */
    public String format(List<Token> tokens) {
        return writeCodeSection(tokens).toString();
    }

    /**
     * Formats the given string using a given lexer
     *
     * @param input input to format
     * @param lexer lexer to use
     * @return resulting code
     */
    public String format(String input, Lexer lexer) {
        return format(lexer.tokenize(input));
    }

    /**
     * Writes and formats a given code section (in form of a list of tokens) into a StringBuilder
     * using a {@link CodeSectionFormatter}
     *
     * @param tokens tokens to write
     * @return written code sections
     */
    private StringBuilder writeCodeSection(List<Token> tokens) {
        CodeSectionFormatter formatter = new CodeSectionFormatter(tokens);

        formatter.format();

        return formatter.result();
    }
}
