package org.togetherjava.tjbot.formatter.formatting;

import org.togetherjava.tjbot.formatter.tokenizer.Token;
import org.togetherjava.tjbot.formatter.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pretty-formats a given list of code tokens.
 * <p>
 * After creation, use {@link #format()}. This is a one-time method.
 */
// Sonar complains about commented out code on multiple methods.
// A false-positive, this is intentional explanation.
@SuppressWarnings("squid:S125")
public final class CodeSectionFormatter {
    private static final String INDENT = " ".repeat(2);

    private final TokenQueue tokens;
    /**
     * The actual set of rules to apply. For example, it decides when to put a space around a token.
     */
    private final FormatterRules rules;
    private final StringBuilder result;

    /**
     * The current level of indentation, which is applied at the start of each new line.
     */
    private int currentIndentLevel;
    /**
     * The current level of generic nesting. For example {@code List<List<Foo>>} has a level of 2 at
     * token {@code Foo}.
     */
    private int currentGenericLevel;
    /**
     * Whether the current token is at the start of a new line.
     */
    private boolean isStartOfLine;
    /**
     * Whether the current line is expected to have multiple semicolons before a line-break is
     * applied. For example in indexed for-loops {@code for (A(); B(); C())}.
     */
    private int expectedSemicolonsInLine;
    private boolean isInPackageDeclaration;
    private boolean isInImportDeclaration;

    private boolean alreadyUsed;

    private static List<Token> patchTokens(List<Token> tokens) {
        // We rebuild the whitespaces ourselves and ignore existing
        List<Token> patchedTokens = new ArrayList<>(tokens);
        patchedTokens.removeIf(token -> token.type() == TokenType.WHITESPACE);

        return patchedTokens;
    }

    /**
     * Creates an instance for formatting the given tokens.
     * <p>
     * The formatter is not backed by the list.
     * 
     * @param tokens to format
     */
    public CodeSectionFormatter(List<Token> tokens) {
        this.tokens = new TokenQueue(patchTokens(tokens));
        result = new StringBuilder(this.tokens.remainingSize());
        rules = new FormatterRules(this.tokens);
    }

    /**
     * Pretty-formats the code tokens of this formatter.
     * <p>
     * This method must only be used once per instance.
     * 
     * @return the formatted code
     */
    public String format() {
        if (alreadyUsed) {
            throw new IllegalStateException(
                    "This method must only be used once, create a new instance instead.");
        }

        while (!tokens.isEmpty()) {
            Token token = tokens.consume();
            process(token);
        }

        String resultText = result.toString();

        // Clear the builder to prevent memory leaks
        result.setLength(0);
        alreadyUsed = true;

        return resultText;
    }

    private void process(Token token) {
        preProcess(token.type());
        putToken(token);
        postProcess(token.type());
    }

    private void preProcess(TokenType tokenType) {
        handleIndents(tokenType);
        preHandleGenericLevel(tokenType);
        handleSpacePrefix(tokenType);
    }

    private void handleIndents(TokenType tokenType) {
        if (tokenType == TokenType.OPEN_BRACES) {
            currentIndentLevel++;
        } else if (tokenType == TokenType.CLOSE_BRACES) {
            currentIndentLevel--;
        }

        if (isStartOfLine) {
            result.append(createIndent());
            isStartOfLine = false;
        }
    }

    private String createIndent() {
        return INDENT.repeat(Math.max(0, currentIndentLevel));
    }

    private void preHandleGenericLevel(TokenType tokenType) {
        // Already inside generics
        if (currentGenericLevel > 0) {
            // List<List<Foo>>
            if (tokenType == TokenType.LESS_THAN) {
                currentGenericLevel++;
            }

            return;
        }

        // Start of generic
        if (rules.isStartOfGeneric(tokenType)) {
            currentGenericLevel = 1;
        }
    }

    private void handleSpacePrefix(TokenType tokenType) {
        if (currentGenericLevel > 0) {
            if (rules.shouldPutSpaceBeforeGeneric(tokenType)) {
                result.append(' ');
            }
            return;
        }

        if (rules.shouldPutSpaceBefore(tokenType)) {
            result.append(' ');
        }
    }

    private void putToken(Token token) {
        String content = token.content();

        if (token.type() == TokenType.MULTI_LINE_COMMENT) {
            content = FormatterRules.patchMultiLineComment(content, createIndent());
        }

        result.append(content);
    }

    private void postProcess(TokenType tokenType) {
        if (tokens.isEmpty()) {
            // Last character needs no post-processing
            return;
        }

        handleSpaceSuffix(tokenType);
        handleNewLineSuffix(tokenType);
        handleExpectedSemicolonsInLine(tokenType);
        postHandleGenericLevel(tokenType);
        handlePackageDeclaration(tokenType);
        handleImportDeclaration(tokenType);
    }

    private void handleSpaceSuffix(TokenType tokenType) {
        if (currentGenericLevel > 0) {
            if (rules.shouldPutSpaceAfterGeneric(tokenType, currentGenericLevel)) {
                result.append(' ');
            }
            return;
        }

        if (rules.shouldPutSpaceAfter(tokenType, expectedSemicolonsInLine)) {
            result.append(' ');
        }
    }

    private void handleNewLineSuffix(TokenType tokenType) {
        if (rules.shouldPutNewlineAfter(tokenType, expectedSemicolonsInLine)) {
            result.append('\n');
            isStartOfLine = true;
        }
    }

    private void handleExpectedSemicolonsInLine(TokenType tokenType) {
        if (expectedSemicolonsInLine > 0 && tokenType == TokenType.SEMICOLON) {
            expectedSemicolonsInLine--;
        } else if (rules.isStartOfIndexedForLoop(tokenType)) {
            expectedSemicolonsInLine = 2;
        }
    }

    private void postHandleGenericLevel(TokenType tokenType) {
        if (currentGenericLevel > 0 && tokenType == TokenType.GREATER_THAN) {
            currentGenericLevel--;
        }
    }

    private void handlePackageDeclaration(TokenType tokenType) {
        if (tokenType == TokenType.PACKAGE) {
            isInPackageDeclaration = true;
            return;
        }

        if (!isInPackageDeclaration) {
            return;
        }

        // package foo.bar.Baz;
        if (tokenType == TokenType.SEMICOLON) {
            // End of package needs an extra empty line
            isInPackageDeclaration = false;
            result.append('\n');
        }
    }

    private void handleImportDeclaration(TokenType tokenType) {
        if (tokenType == TokenType.IMPORT) {
            isInImportDeclaration = true;
            return;
        }

        if (!isInImportDeclaration) {
            return;
        }

        // import foo.bar.Baz;
        if (tokenType == TokenType.SEMICOLON) {
            isInImportDeclaration = false;
            if (rules.isEndOfLastImportDeclaration()) {
                // End of last import needs an extra empty line
                result.append('\n');
            }
        }
    }
}
