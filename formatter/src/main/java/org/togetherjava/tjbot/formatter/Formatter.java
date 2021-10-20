package org.togetherjava.tjbot.formatter;

import org.togetherjava.tjbot.formatter.tokenizer.Lexer;
import org.togetherjava.tjbot.formatter.tokenizer.Token;
import org.togetherjava.tjbot.formatter.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
     */
    public String format(String input, Lexer lexer) {
        return format(lexer.tokenize(input));
    }

    /**
     * Joins given tokens together and normalizes whitespaces
     *
     * @param tokens tokens to join
     * @return joined form of the tokens
     */
    private String joinTokens(List<Token> tokens) {
        return tokens.stream().map(Token::content).collect(Collectors.joining());
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

    /**
     * Indexes tokens to contain information about whether they are code tokens or not
     *
     * @param tokens not-indexed tokens
     * @return indexed tokens
     */
    private List<CheckedToken> indexTokens(List<Token> tokens) {
        return tokens.stream()
            .map(token -> new CheckedToken(token, isTokenPartOfCode(token)))
            .toList();
    }

    /**
     * Checks if a given token could be part of code
     *
     * @param token token to check
     * @return true if it's a code token, false if not
     */
    private boolean isTokenPartOfCode(Token token) {
        return token.type() != TokenType.UNKNOWN;
    }

    /**
     * Sectionizes a given list of tokens into sections who are either code sections or non-code
     * sections. It decides so by using the internal {@code isTokenPartOfCode} method.
     *
     * @param checkedTokens checked tokens
     * @return list of sections
     */
    private List<Section> sectionize(List<CheckedToken> checkedTokens) {
        CheckedToken first = checkedTokens.get(0);
        Section currentSection = new Section(new ArrayList<>(), first.isCode());
        List<Section> result = new ArrayList<>();

        currentSection.tokens().add(first.token());

        for (int i = 1; i < checkedTokens.size(); i++) {
            CheckedToken next = checkedTokens.get(i);

            if (currentSection.isCodeSection() != next.isCode()) {
                result.add(currentSection);

                currentSection = new Section(new ArrayList<>(), next.isCode());
            }

            currentSection.tokens().add(next.token());
        }

        result.add(currentSection);

        return result;
    }

    /**
     * Section POJR
     */
    private static record Section(List<Token> tokens, boolean isCodeSection) {
    }

    /**
     * CheckedToken POJR
     */
    private static record CheckedToken(Token token, boolean isCode) {
    }
}
