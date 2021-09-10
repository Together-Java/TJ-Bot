package org.togetherjava.formatter;

import org.togetherjava.formatter.tokenizer.Lexer;
import org.togetherjava.formatter.tokenizer.Token;
import org.togetherjava.formatter.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formatter which can format a given string into a string which contains code blocks etc
 *
 * @author illuminator3
 */
public class Formatter {
    /**
     * Set of tokens who should not be put after a space:<br>
     * - DOT<br>
     * - SEMICOLON<br>
     * - NOT<br>
     * - OPEN_PARENTHESIS<br>
     * - CLOSE_PARENTHESIS<br>
     * - OPEN_BRACKETS<br>
     * - CLOSE_BRACKETS<br>
     * - SMALLER<br>
     * - BIGGER<br>
     * - COMMA<br>
     * - FOR<br>
     * - IF<br>
     * - WHILE<br>
     * - PLUSPLUS<br>
     * - MINUSMINUS<br>
     * - RET URN<br>
     * - THIS<br>
     * - PUBLIC<br>
     * - PROTECTED<br>
     * - PRIVATE<br>
     * - TRY<br>
     * - CATCH<br>
     * - PACKAGE<br>
     * - METHOD_REFERENCE<br>
     * - ELSE_IF<br>
     * - ELSE<br>
     */
    private static final Set<TokenType> NON_SPACE_TOKENS = Set.of(TokenType.DOT,
            TokenType.SEMICOLON, TokenType.NOT, TokenType.OPEN_PARENTHESIS,
            TokenType.CLOSE_PARENTHESIS, TokenType.OPEN_BRACKETS, TokenType.CLOSE_BRACKETS,
            TokenType.SMALLER, TokenType.BIGGER, TokenType.COMMA, TokenType.FOR, TokenType.IF,
            TokenType.WHILE, TokenType.PLUSPLUS, TokenType.MINUSMINUS, TokenType.RETURN,
            TokenType.THIS, TokenType.PUBLIC, TokenType.PROTECTED, TokenType.PRIVATE, TokenType.TRY,
            TokenType.CATCH, TokenType.PACKAGE, TokenType.METHOD_REFERENCE, TokenType.ELSE_IF,
            TokenType.ELSE);

    /**
     * Formats the given tokens
     *
     * @param tokens tokens to format
     * @return resulting code
     * @author illuminator3
     */
    public String format(List<Token> tokens) {
        List<Section> sections = sectionize(indexTokens(tokens));
        StringBuilder result = new StringBuilder();

        for (Section section : sections) {
            if (section.isCodeSection()) {
                result.append("```java\n")
                      .append(writeCodeSection(section.tokens()))
                      .append("\n```");
            } else {
                result.append(joinTokens(section.tokens()));
            }
        }

        return result.toString();
    }

    /**
     * Formats the given string using a given lexer
     *
     * @param input input to format
     * @param lexer lexer to use
     * @return resulting code
     * @author illuminator3
     */
    public String format(String input, Lexer lexer) {
        return format(lexer.tokenize(input));
    }

    /**
     * Joins given tokens together and normalizes whitespaces
     *
     * @param tokens tokens to join
     * @return joined form of the tokens
     * @author illuminator3
     */
    private String joinTokens(List<Token> tokens) {
        return tokens.stream().map(Token::content).collect(Collectors.joining());
    }

    /**
     * Writes and formats a given code section (in form of a list of tokens) into a StringBuilder
     *
     * @param tokens tokens to write
     * @return written code sections
     * @author illuminator3
     */
    private StringBuilder writeCodeSection(List<Token> tokens) {
        List<Token> normalizedTokens = makeMutable(tokens);

        purgeWhitespaces(normalizedTokens);

        TokenType lastToken = TokenType.UNKNOWN;
        StringBuilder result = new StringBuilder();
        boolean lastNewLine = false;
        int indentation = 0;
        boolean forClosed = true;
        int forClosingLevel = 0;

        for (Token token : normalizedTokens) {
            TokenType type = token.type();

            if (lastNewLine && type != TokenType.CLOSE_BRACES) {
                putIndentation(indentation, result);
            }

            lastNewLine = false;

            iteration: {
                if (type == TokenType.OPEN_BRACES) {
                    if (lastToken == TokenType.CLOSE_PARENTHESIS
                            || lastToken == TokenType.IDENTIFIER || lastToken == TokenType.TRY || lastToken == TokenType.ELSE) {
                        result.append(" ");
                    }

                    append(result, token);

                    result.append("\n");

                    lastNewLine = true;
                    indentation++;

                    break iteration;
                } else if (lastToken == TokenType.CLOSE_BRACKETS && type != TokenType.SEMICOLON) {
                    result.append(" ");

                    append(result, token);

                    break iteration;
                } else if (type == TokenType.CLOSE_BRACES) {
                    result.append("\n");

                    putIndentation(--indentation, result);

                    append(result, token);

                    result.append("\n");

                    lastNewLine = true;

                    break iteration;
                } else if (type == TokenType.IDENTIFIER && (lastToken == TokenType.CLOSE_BRACES
                        || lastToken == TokenType.OPEN_BRACES
                        || lastToken == TokenType.SEMICOLON
                        || lastToken == TokenType.UNKNOWN)) {
                    append(result, token);

                    break iteration;
                } else if (type == TokenType.COMMENT || type == TokenType.SEMICOLON
                        || type == TokenType.ANNOTATION) {
                    if (type == TokenType.COMMENT) {
                        result.append(" ");
                    }

                    append(result, token);

                    if (forClosed) {
                        result.append("\n");

                        lastNewLine = true;
                    }

                    break iteration;
                } else if ((type == TokenType.OPEN_PARENTHESIS && lastToken != TokenType.IDENTIFIER
                        && lastToken != TokenType.NOT && lastToken != TokenType.BIGGER
                        && lastToken != TokenType.OPEN_PARENTHESIS) || lastToken == TokenType.PUBLIC
                        || lastToken == TokenType.PRIVATE || lastToken == TokenType.PROTECTED
                        || (lastToken == TokenType.BIGGER && type != TokenType.OPEN_PARENTHESIS)
                        || lastToken == TokenType.RETURN || lastToken == TokenType.COMMA
                        || lastToken == TokenType.CATCH
                        || (lastToken == TokenType.CLOSE_PARENTHESIS
                                && type != TokenType.CLOSE_PARENTHESIS && type != TokenType.DOT
                                && type != TokenType.COMMA)
                        || lastToken == TokenType.PACKAGE) {
                    result.append(" ");
                }

                if (!forClosed) {
                    if (type == TokenType.OPEN_PARENTHESIS) {
                        forClosingLevel++;
                    } else if (type == TokenType.CLOSE_PARENTHESIS) {
                        forClosingLevel--;
                    }

                    if (forClosingLevel == 0) {
                        forClosed = true;
                    }
                }

                if (type == TokenType.FOR) {
                    forClosed = false;
                }

                if (!(NON_SPACE_TOKENS.contains(type) || NON_SPACE_TOKENS.contains(lastToken))) {
                    result.append(" ");
                }

                append(result, token);
            }

            lastToken = type;
        }

        return new StringBuilder(result.toString().replaceAll("([;}])\n\n", "$1\n"));
    }

    /**
     * Puts the needed indentation into a StringBuilder
     *
     * @param indentation indentation level
     * @param sb string builder
     * @author illuminator3
     */
    private void putIndentation(int indentation, StringBuilder sb) {
        sb.append("    ".repeat(indentation));
    }

    /**
     * Appends a token to a StringBuilder
     *
     * @param sb string builder
     * @param token token to append
     * @author illuminator3
     */
    private void append(StringBuilder sb, Token token) {
        sb.append(token.content());
    }

    /**
     * Makes a list mutable
     *
     * @param in (im)mutable list
     * @return mutable list
     * @author illuminator3
     */
    private <T> List<T> makeMutable(List<T> in) {
        return new ArrayList<>(in);
    }

    /**
     * Removes every whitespace from a given list of tokens
     *
     * @param tokens tokens to remove whitesapces from
     * @author illuminator3
     */
    private void purgeWhitespaces(List<Token> tokens) {
        tokens.removeIf(t -> t.type() == TokenType.WHITESPACE);
    }

    /**
     * Indexes tokens to contain information about whether they are code tokens or not
     *
     * @param tokens not-indexed tokens
     * @return indexed tokens
     * @author illuminator3
     */
    private List<IndexedToken> indexTokens(List<Token> tokens) {
        return tokens.stream()
                     .map(token -> new IndexedToken(token, isTokenPartOfCode(token)))
                     .toList();
    }

    /**
     * Checks if a given token could be part of code
     *
     * @param token token to check
     * @return true if it's a code token, false if not
     * @author illuminator3
     */
    private boolean isTokenPartOfCode(Token token) {
        return token.type() != TokenType.UNKNOWN;
    }

    /**
     * Sectionizes a given list of tokens into sections who are code sections and sections who are not
     *
     * @param indexedTokens indexed tokens
     * @return list of sections
     * @author illuminator3
     */
    private List<Section> sectionize(List<IndexedToken> indexedTokens) {
        IndexedToken first = indexedTokens.get(0);
        Section currSec = new Section(new ArrayList<>(), first.isCode());
        List<Section> result = new ArrayList<>();

        currSec.tokens().add(first.token());

        for (int i = 1; i < indexedTokens.size(); i++) {
            IndexedToken next = indexedTokens.get(i);

            if (currSec.isCodeSection() != next.isCode()) {
                result.add(currSec);

                currSec = new Section(new ArrayList<>(), next.isCode());
            }

            currSec.tokens().add(next.token());
        }

        result.add(currSec);

        return result;
    }

    /**
     * Section POJR
     *
     * @author illuminator3
     */
    private static record Section(List<Token> tokens, boolean isCodeSection) {
    }

    /**
     * IndexedToken POJR
     *
     * @author illuminator3
     */
    private static record IndexedToken(Token token, boolean isCode) {
    }
}
