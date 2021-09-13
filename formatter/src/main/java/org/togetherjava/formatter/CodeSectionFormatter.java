package org.togetherjava.formatter;

import org.togetherjava.formatter.tokenizer.Token;
import org.togetherjava.formatter.tokenizer.TokenType;
import org.togetherjava.formatter.util.SkippableLookaheadArrayDeque;
import org.togetherjava.formatter.util.SkippableLookaheadQueue;

import java.util.*;

class CodeSectionFormatter {
    private final StringBuilder result = new StringBuilder();
    private final SkippableLookaheadQueue<Token> queue;

    private int indentation;
    private boolean applyIndentation;
    private int forLevel;
    private int genericDepth;
    private int enhancedLevel = -1;

    CodeSectionFormatter(List<Token> tokens) {
        this(new SkippableLookaheadArrayDeque<>(tokens));
    }

    CodeSectionFormatter(SkippableLookaheadQueue<Token> queue) {
        this.queue = queue;

        purgeWhitespaces(this.queue);
    }

    private static void purgeWhitespaces(Queue<Token> queue) {
        queue.removeIf(t -> t.type() == TokenType.WHITESPACE);
    }

    void format() {
        Token next;

        while (!queue.isEmpty() && (next = queue.remove()) != null) {
            updateIndentation(next.type());
            applyIndentation();
            consume(next);
        }
    }

    private void consume(Token token) {
        TokenType type = token.type();

        if (checkGeneric(type)) {
            result.append(token.content());

            return;
        }

        if (genericDepth != 0) {
            handleGeneric(token);

            return;
        }

        checkFor(type);
        updateEnhancedLevel(type);
        put(token);
    }

    private void put(Token token) {
        TokenType type = token.type();

        if (isOperator(token)) { // apply space before an operator (like +, - and *)
            result.append(' ');
        }

        result.append(token.content());

        if (shouldPutSpaceAfter(token)) {
            result.append(' ');
        } else if (shouldPutNewLineAfter(type)) { // put a new line after { and ; and } and comments
                                                  // and annotations
            appendNewLine();

            if (type == TokenType.SEMICOLON) {
                forLevel--;
            }

            applyIndentation = true;
        }
    }

    private boolean shouldPutSpaceAfter(Token token) {
        TokenType type = token.type();

        return isKeyword(token) || isOperator(token) || isParenthesisRule(token)
                || type == TokenType.CLOSE_BRACKETS // put a space after e.g. 'try' or 'else' and
                                                    // ] or if it's an operator
                || type == TokenType.COMMA // for e.g. multiarg method calls or method parameters
                || (type == TokenType.IDENTIFIER && !queue.isEmpty()
                        && queue.peek().type() == TokenType.IDENTIFIER); // for double identifier
                                                                         // in e.g. method
                                                                         // declaration or enhanced
                                                                         // for loops
    }

    private void updateEnhancedLevel(TokenType type) {
        if (enhancedLevel != -1) {
            switch (type) {
                case OPEN_PARENTHESIS -> enhancedLevel++;
                case CLOSE_PARENTHESIS -> enhancedLevel--;
            }
        }
    }

    private void checkFor(TokenType type) {
        if (isIndexedForLoop(type)) { // if it's a for int loop then set the forLevel to 2
            forLevel = 2;
        } else if (isEnhancedForLoop(type)) {
            enhancedLevel = 0;
        }
    }

    private void handleGeneric(Token token) {
        TokenType type = token.type();

        if (type == TokenType.EXTENDS || type == TokenType.SUPER) {
            result.append(' ');
        }

        result.append(token.content());

        if (type == TokenType.COMMA || type == TokenType.WILDCARD || type == TokenType.EXTENDS
                || type == TokenType.SUPER) {
            result.append(' ');
        }

        // ------------------------------------------------

        switch (type) {
            case SMALLER -> genericDepth++;
            case BIGGER -> genericDepth--;
        }

        if (genericDepth == 0 && !queue.isEmpty()
                && queue.peek().type() != TokenType.OPEN_PARENTHESIS) { // last closing except if
                                                                        // there's an opening
                                                                        // parenthesis after it
            result.append(' ');
        }
    }

    private boolean checkGeneric(TokenType type) {
        if (type == TokenType.SMALLER) {
            int depth = 1;

            for (int i = 0;; i++) {
                Token next = queue.peek(i);

                if (next == null || depth == 0) {
                    break;
                }

                TokenType nextType = next.type();

                if (!isValidGeneric(nextType)) {
                    return false;
                }

                switch (nextType) {
                    case SMALLER -> depth++;
                    case BIGGER -> depth--;
                }
            }

            genericDepth = 1;

            return true;
        }

        return false;
    }

    private boolean isValidGeneric(TokenType type) {
        return type == TokenType.WILDCARD || type == TokenType.SMALLER || type == TokenType.BIGGER
                || type == TokenType.COMMA || type == TokenType.DOT || type == TokenType.EXTENDS
                || type == TokenType.SUPER || type == TokenType.IDENTIFIER;
    }

    private boolean shouldPutNewLineAfter(TokenType type) {
        return type == TokenType.OPEN_BRACES || type == TokenType.SEMICOLON
                || type == TokenType.COMMENT || type == TokenType.ANNOTATION
                || (type == TokenType.CLOSE_BRACES && !queue.isEmpty()
                        && queue.peek().type() != TokenType.SEMICOLON); // don't put new lines after
                                                                        // braces if they're part
                                                                        // of a lambda:
                                                                        // () -> {
                                                                        // };
    }

    private boolean isIndexedForLoop(TokenType type) {
        return type == TokenType.FOR && !internalEnhancedFor();
    }

    private boolean isEnhancedForLoop(TokenType type) {
        return type == TokenType.FOR && internalEnhancedFor();
    }

    private boolean internalEnhancedFor() {
        return queue.peek(3, t -> {
            TokenType ttype = t.type();

            return ttype == TokenType.ANNOTATION || ttype == TokenType.FINAL;
        }).type() == TokenType.COLON;
    }

    /**
     * Parenthesis rule: append a space after a closing parenthesis if the next token isn't another
     * closing parenthesis, an operator or a semicolon
     */
    private boolean isParenthesisRule(Token token) {
        if (queue.isEmpty()) {
            return false;
        }

        Token next = queue.peek();
        TokenType nextType = next.type();

        return token.type() == TokenType.CLOSE_PARENTHESIS
                && nextType != TokenType.CLOSE_PARENTHESIS && nextType != TokenType.SEMICOLON
                && !isOperator(next);
    }

    /**
     * Appends a new line if there's more in the token queue
     *
     * @author illuminator3
     */
    private void appendNewLine() {
        if (!queue.isEmpty()) {
            if (forLevel <= 0) {
                result.append('\n');
            } else {
                result.append(' '); // put a space after a ; in a for loop
            }
        }
    }

    private boolean isKeyword(Token token) {
        return token.type().isKeyword();
    }

    private boolean isOperator(Token token) {
        return token.type().isOperator();
    }

    private void updateIndentation(TokenType type) {
        switch (type) {
            case OPEN_BRACES -> indentation++;
            case CLOSE_BRACES -> indentation--;
        }
    }

    private void applyIndentation() {
        if (applyIndentation) {
            result.append("    ".repeat(Math.max(0, indentation)));

            applyIndentation = false;
        }
    }

    StringBuilder result() {
        return result;
    }
}
