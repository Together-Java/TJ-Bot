package org.togetherjava.formatter;

import org.togetherjava.formatter.tokenizer.Token;
import org.togetherjava.formatter.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Formatter {
    public String format(List<Token> tokens) {
        List<Section> sections = sectionize(indexTokens(tokens));
        StringBuilder result = new StringBuilder();

        for (Section section : sections) {
            if (section.isCodeSection()) {
                result.append("```java\n").append(writeCodeSection(section.tokens())).append("\n```");
            } else {
                result.append(revert(section.tokens()));
            }
        }

        return result.toString();
    }

    private String revert(List<Token> tokens) {
        return tokens.stream().map(Token::content).collect(Collectors.joining());
    }

    private StringBuilder writeCodeSection(List<Token> tokens) {
        purgeWhitespaces(tokens = makeMutable(tokens));

        TokenType lastToken = TokenType.UNKNOWN;
        StringBuilder result = new StringBuilder();
        boolean lastNewLine = false;
        int indentation = 0;
        boolean forClosed = true;
        int forClosingLevel = 0;

        for (Token token : tokens) {
            TokenType type = token.type();

            if (lastNewLine && type != TokenType.CLOSE_BRACES) {
                putIndentation(indentation, result);
            }

            lastNewLine = false;

            iteration: {
                if (type == TokenType.OPEN_BRACES) {
                    if (lastToken == TokenType.CLOSE_PARENTHESIS || lastToken == TokenType.IDENTIFIER || lastToken == TokenType.TRY) {
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
                } else if (type == TokenType.IDENTIFIER && (lastToken == TokenType.CLOSE_BRACES || lastToken == TokenType.OPEN_BRACES || lastToken == TokenType.SEMICOLON)) {
                    append(result, token);

                    break iteration;
                } else if (type == TokenType.COMMENT || type == TokenType.SEMICOLON || type == TokenType.ANNOTATION) {
                    if (type == TokenType.COMMENT) {
                        result.append(" ");
                    }

                    append(result, token);

                    if (forClosed) {
                        result.append("\n");

                        lastNewLine = true;
                    }

                    break iteration;
                } else if (
                    (type == TokenType.OPEN_PARENTHESIS && lastToken != TokenType.IDENTIFIER && lastToken != TokenType.NOT && lastToken != TokenType.BIGGER && lastToken != TokenType.OPEN_PARENTHESIS) ||
                    lastToken == TokenType.PUBLIC ||
                    lastToken == TokenType.PRIVATE ||
                    lastToken == TokenType.PROTECTED ||
                    (lastToken == TokenType.BIGGER && type != TokenType.OPEN_PARENTHESIS) ||
                    lastToken == TokenType.RETURN ||
                    lastToken == TokenType.COMMA ||
                    lastToken == TokenType.CATCH ||
                    (lastToken == TokenType.CLOSE_PARENTHESIS && type != TokenType.CLOSE_PARENTHESIS && type != TokenType.DOT && type != TokenType.COMMA) ||
                    lastToken == TokenType.PACKAGE
                ) {
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

                List<TokenType> check = List.of(
                    TokenType.DOT,
                    TokenType.SEMICOLON,
                    TokenType.NOT,
                    TokenType.OPEN_PARENTHESIS,
                    TokenType.CLOSE_PARENTHESIS,
                    TokenType.OPEN_BRACKETS,
                    TokenType.CLOSE_BRACKETS,
                    TokenType.SMALLER,
                    TokenType.BIGGER,
                    TokenType.COMMA,
                    TokenType.FOR,
                    TokenType.IF,
                    TokenType.WHILE,
                    TokenType.PLUSPLUS,
                    TokenType.MINUSMINUS,
                    TokenType.RETURN,
                    TokenType.THIS,
                    TokenType.PUBLIC,
                    TokenType.PROTECTED,
                    TokenType.PRIVATE,
                    TokenType.TRY,
                    TokenType.CATCH,
                    TokenType.PACKAGE,
                    TokenType.METHOD_REFERENCE,
                    TokenType.ELSE_IF
                );

                if (!(check.contains(type) || check.contains(lastToken))) {
                    result.append(" ");
                }

                append(result, token);
            }

            lastToken = type;
        }

        return new StringBuilder(result.toString().replaceAll("([;}])\n\n", "$1\n"));
    }

    private void putIndentation(int indentation, StringBuilder sb) {
        IntStream.range(0, indentation).forEach(n -> sb.append("\t"));
    }

    private void append(StringBuilder sb, Token token) {
        sb.append(token.content());
    }

    private <T> List<T> makeMutable(List<T> in) {
        return new ArrayList<>(in);
    }

    private void purgeWhitespaces(List<Token> tokens) {
        tokens.removeIf(t -> t.type() == TokenType.WHITSPACE);
    }

    private List<IndexedToken> indexTokens(List<Token> tokens) {
        List<IndexedToken> result = new ArrayList<>();

        for (Token token : tokens) {
            result.add(new IndexedToken(token, isCodeToken(token)));
        }

        return result;
    }

    private boolean isCodeToken(Token token) {
        return token.type() != TokenType.UNKNOWN;
    }

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

    private static record Section(List<Token> tokens, boolean isCodeSection) {}
    private static record IndexedToken(Token token, boolean isCode) {}
}
