package org.togetherjava.tjbot.formatter;

import org.togetherjava.tjbot.formatter.formatting.CodeSectionFormatter;
import org.togetherjava.tjbot.formatter.tokenizer.Lexer;
import org.togetherjava.tjbot.formatter.tokenizer.Token;

import java.util.List;

/**
 * Formats code given as string. See {@link #format(CharSequence)}.
 * <p>
 * Best results are achieved for Java code.
 */
public final class Formatter {
    private final Lexer lexer = new Lexer();

    /**
     * Formats the given string.
     * <p>
     * Best results are achieved for Java code.
     *
     * @param code the code to format
     * @return the formatted code
     */
    public String format(CharSequence code) {
        List<Token> tokens = lexer.tokenize(code);
        CodeSectionFormatter codeFormatter = new CodeSectionFormatter(tokens);

        return codeFormatter.format();
    }
}
