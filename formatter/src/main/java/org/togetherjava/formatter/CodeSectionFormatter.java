package org.togetherjava.formatter;

import org.togetherjava.formatter.tokenizer.Token;
import org.togetherjava.formatter.tokenizer.TokenType;
import org.togetherjava.formatter.util.SkippableLookaheadArrayDeque;
import org.togetherjava.formatter.util.SkippableLookaheadQueue;

import java.util.*;
import java.util.function.Predicate;

/**
 * Formatter which specifically formats code tokens (that are part of a section)
 *
 * @author illuminator3
 */
class CodeSectionFormatter {
    private final StringBuilder result = new StringBuilder();
    private final SkippableLookaheadQueue<Token> queue;

    private int indentation;
    private boolean applyIndentation;
    private int forLevel;
    private int genericDepth;

    CodeSectionFormatter(List<Token> tokens) {
        this(new SkippableLookaheadArrayDeque<>(tokens));
    }

    CodeSectionFormatter(SkippableLookaheadQueue<Token> queue) {
        this.queue = queue;

        purgeWhitespaces(this.queue);
    }

    /**
     * Removes all whitespaces from the given queue
     *
     * @param queue the queue to remove whitespaces from
     * @author illuminator3
     */
    private static void purgeWhitespaces(Queue<Token> queue) {
        queue.removeIf(t -> t.type() == TokenType.WHITESPACE);
    }

    /**
     * Starts the formatting process
     *
     * @author illuminator3
     */
    void format() {
        Token next;

        while (!queue.isEmpty() && (next = queue.remove()) != null) {
            updateIndentation(next.type());
            applyIndentation();
            consume(next);
        }
    }

    /**
     * Consumes the next token
     *
     * @param token token to consume
     * @author illuminator3
     */
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
        put(token);
    }

    /**
     * Puts the next token into the result
     *
     * @param token token to put
     * @author illuminator3
     */
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

    /**
     * Checks if a space should be put after a given token
     *
     * @param token token to check
     * @return wether a space should be put after that token
     * @author illuminator3
     */
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

    /**
     * Checks if a given token type belongs to a for loop (uses
     * {@link SkippableLookaheadQueue#peek(int, Predicate)}
     *
     * @param type current token type
     * @author illuminator3
     */
    private void checkFor(TokenType type) {
        if (isIndexedForLoop(type)) { // if it's a for int loop then set the forLevel to 2
            forLevel = 2;
        }
    }

    /**
     * Handles the case of being inside a generic type declaration
     *
     * @param token current token
     * @author illuminator3
     */
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

        if (type == TokenType.LESS_THAN) {
            genericDepth++;
        } else if (type == TokenType.GREATER_THAN) {
            genericDepth--;
        }

        if (genericDepth == 0 && !queue.isEmpty()
                && queue.peek().type() != TokenType.OPEN_PARENTHESIS) { // last closing except if
                                                                        // there's an opening
                                                                        // parenthesis after it
            result.append(' ');
        }
    }

    /**
     * Checks if a given token type belongs to a generic type declaration (uses
     * {@link org.togetherjava.formatter.util.LookaheadQueue#peek(int)}
     *
     * @param type current token type
     * @return wether the token type belongs to a generic type declaration
     * @author illuminator3
     */
    private boolean checkGeneric(TokenType type) {
        if (type == TokenType.LESS_THAN) {
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

                if (nextType == TokenType.LESS_THAN) {
                    depth++;
                } else if (nextType == TokenType.GREATER_THAN) {
                    depth--;
                }
            }

            genericDepth = 1;

            return true;
        }

        return false;
    }

    /**
     * Checks if a given token type would be valid inside a generic type declaration
     *
     * @param type token type to check
     * @return wether it's valid inside a generic type declaration
     * @author illuminator3
     */
    private boolean isValidGeneric(TokenType type) {
        return type == TokenType.WILDCARD || type == TokenType.LESS_THAN
                || type == TokenType.GREATER_THAN || type == TokenType.COMMA
                || type == TokenType.DOT || type == TokenType.EXTENDS || type == TokenType.SUPER
                || type == TokenType.IDENTIFIER;
    }

    /**
     * Checks if a new line should be put after a given token type
     *
     * @param type token type to check
     * @return wether a new line should be put after that token
     * @author illuminator3
     */
    private boolean shouldPutNewLineAfter(TokenType type) {
        if (type == TokenType.OPEN_BRACES || type == TokenType.SEMICOLON
                || type == TokenType.COMMENT || type == TokenType.ANNOTATION) {
            return true;
        }

        // don't put new lines after braces if they're part of a lambda:
        // () -> {}<no break here>; // NOSONAR
        return type == TokenType.CLOSE_BRACES && !queue.isEmpty()
                && queue.peek().type() != TokenType.SEMICOLON;
    }

    /**
     * Checks if there's an indexed for loop ahead (uses
     * {@link SkippableLookaheadQueue#peek(int, Predicate)})
     *
     * @param type current token type
     * @return wether there's an indexed for loop or not
     * @author illuminator3
     */
    private boolean isIndexedForLoop(TokenType type) {
        return type == TokenType.FOR && !internalEnhancedFor();
    }

    /**
     * Checks if there's an enhanced for loop ahead without checking the current token type
     *
     * @return wether there's an enhanced for loop ahead
     * @author illuminator3
     */
    private boolean internalEnhancedFor() {
        return queue.peek(3, t -> {
            TokenType ttype = t.type();

            return ttype == TokenType.ANNOTATION || ttype == TokenType.FINAL;
        }).type() == TokenType.COLON;
    }

    /**
     * Parenthesis rule: append a space after a closing parenthesis if the next token isn't another
     * closing parenthesis, an operator or a semicolon
     *
     * @return wether a space should be put after the parenthesis
     * @author illuminator3
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

    /**
     * Checks if a given token is a keyword using {@link TokenType#isKeyword()}
     *
     * @param token token to check
     * @return wether the given token is a keyword
     * @author illuminator3
     */
    private boolean isKeyword(Token token) {
        return token.type().isKeyword();
    }

    /**
     * Checks if a given token is an operator using {@link TokenType#isOperator()}
     *
     * @param token token to check
     * @return wether the given token is an operator
     * @author illuminator3
     */
    private boolean isOperator(Token token) {
        return token.type().isOperator();
    }

    /**
     * Updates the indentation based on the current token type
     *
     * @param type current token type
     * @author illuminator3
     */
    private void updateIndentation(TokenType type) {
        if (type == TokenType.OPEN_BRACES) {
            indentation++;
        } else if (type == TokenType.CLOSE_BRACES) {
            indentation--;
        }
    }

    /**
     * Applies the current indentation
     *
     * @author illuminator3
     */
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
