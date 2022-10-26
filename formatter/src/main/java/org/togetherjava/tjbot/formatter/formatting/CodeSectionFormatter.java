package org.togetherjava.tjbot.formatter.formatting;

import org.togetherjava.tjbot.formatter.tokenizer.Token;
import org.togetherjava.tjbot.formatter.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;

// TODO Javadoc
public final class CodeSectionFormatter {
    private static final String INDENT = " ".repeat(2);

    private final TokenQueue tokens;
    private final FormatterRules rules;
    private final StringBuilder result;

    private int currentIndentLevel;
    private int currentGenericLevel;
    private boolean isStartOfLine;
    private int expectedSemicolonsInLine;

    private static List<Token> patchTokens(List<Token> tokens) {
        // We rebuild the whitespaces ourselves and ignore existing
        List<Token> patchedTokens = new ArrayList<>(tokens);
        patchedTokens.removeIf(token -> token.type() == TokenType.WHITESPACE);

        return patchedTokens;
    }

    public CodeSectionFormatter(List<Token> tokens) {
        this.tokens = new TokenQueue(patchTokens(tokens));
        result = new StringBuilder(this.tokens.remainingSize());
        rules = new FormatterRules(this.tokens);
    }

    public String format() {
        while (!tokens.isEmpty()) {
            Token token = tokens.consume();
            process(token);
        }

        return result.toString();
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
            result.append(INDENT.repeat(Math.max(0, currentIndentLevel)));
            isStartOfLine = false;
        }
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
        if (tokenType == TokenType.LESS_THAN && rules.isStartOfGeneric()) {
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
            // TODO Doesnt work as intended
            content = content.stripIndent();
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
}
