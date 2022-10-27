package org.togetherjava.tjbot.formatter.formatting;

import org.togetherjava.tjbot.formatter.tokenizer.TokenType;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Rules used by {@link CodeSectionFormatter} to format code.
 */
// Sonar complains about commented out code on multiple methods.
// A false-positive, this is intentional explanation.
@SuppressWarnings("squid:S125")
final class FormatterRules {
    private final TokenQueue tokens;

    /**
     * Creates a set of rules for the given tokens.
     *
     * @param tokens to format with rules of this instance, read-only
     */
    FormatterRules(TokenQueue tokens) {
        this.tokens = tokens;
    }

    private static boolean matchesAnyRule(TokenType tokenType,
            Collection<? extends Predicate<TokenType>> rules) {
        return rules.stream().anyMatch(rule -> rule.test(tokenType));
    }

    boolean shouldPutSpaceBeforeGeneric(TokenType tokenType) {
        return Set.of(TokenType.EXTENDS, TokenType.SUPER).contains(tokenType);
    }

    boolean shouldPutSpaceBefore(TokenType tokenType) {
        // 5 + 3
        List<Predicate<TokenType>> rules =
                List.of(type -> type.getAttribute() == TokenType.Attribute.BINARY_OPERATOR);
        return matchesAnyRule(tokenType, rules);
    }

    boolean shouldPutSpaceAfterGeneric(TokenType tokenType, int currentGenericLevel) {
        if (currentGenericLevel <= 0) {
            return false;
        }

        if (currentGenericLevel == 1 && tokenType == TokenType.GREATER_THAN) {
            // End of generics
            // With space: List<Integer> values
            // No space: new ArrayList<>()
            return tokens.peekType() != TokenType.OPEN_PARENTHESIS;
        }

        return Set.of(TokenType.COMMA, // Map<Foo, Bar>
                TokenType.QUESTION_MARK, // List<? super Foo>
                TokenType.EXTENDS, // List<? extends Foo>
                TokenType.SUPER) // List<? super Foo>
            .contains(tokenType);
    }

    boolean shouldPutSpaceAfter(TokenType tokenType, int expectedSemicolonsInLine) {
        List<Predicate<TokenType>> rules = List.of(
                type -> type.getAttribute() == TokenType.Attribute.KEYWORD, // class Foo
                type -> type.getAttribute() == TokenType.Attribute.BINARY_OPERATOR, // 5 + 3
                this::shouldPutSpaceAfterClosingParenthesis, // foo() {
                TokenType.CLOSE_BRACKETS::equals, // foo[i] = 3
                TokenType.COMMA::equals, // foo(x, y)
                // String toString()
                type -> type == TokenType.IDENTIFIER && tokens.peekType() == TokenType.IDENTIFIER,
                // for (a(); b(); c())
                type -> type == TokenType.SEMICOLON && expectedSemicolonsInLine > 0);

        return matchesAnyRule(tokenType, rules);
    }

    private boolean shouldPutSpaceAfterClosingParenthesis(TokenType tokenType) {
        if (tokenType != TokenType.CLOSE_PARENTHESIS) {
            return false;
        }

        TokenType nextType = tokens.peekType();
        if (nextType == TokenType.CLOSE_PARENTHESIS) {
            return false; // foo(bar())
        }
        if (nextType == TokenType.SEMICOLON) {
            return false; // foo();
        }
        if (nextType.getAttribute() == TokenType.Attribute.BINARY_OPERATOR) {
            // The space is added before the operator already
            return false; // foo() + 3
        }
        if (nextType == TokenType.DOT) {
            return false; // foo().bar()
        }

        return true; // foo() {
    }

    boolean shouldPutNewlineAfter(TokenType tokenType, int expectedSemicolonsInLine) {
        List<Predicate<TokenType>> rules = List.of(TokenType.OPEN_BRACES::equals, // foo() {
                TokenType.SINGLE_LINE_COMMENT::equals, // // Foo
                TokenType.MULTI_LINE_COMMENT::equals, // /* Foo */
                TokenType.ANNOTATION::equals, // @Foo
                // } but not };
                type -> type == TokenType.CLOSE_BRACES && tokens.peekType() != TokenType.SEMICOLON,
                // int x = 5; but not for (;;)
                type -> type == TokenType.SEMICOLON && expectedSemicolonsInLine == 0);

        return matchesAnyRule(tokenType, rules);
    }

    boolean isStartOfGeneric(TokenType tokenType) {
        if (tokenType != TokenType.LESS_THAN) {
            return false;
        }

        Set<TokenType> typesAllowedInGenerics = Set.of(TokenType.LESS_THAN, TokenType.GREATER_THAN,
                TokenType.QUESTION_MARK, TokenType.EXTENDS, TokenType.SUPER, TokenType.COMMA,
                TokenType.DOT, TokenType.IDENTIFIER);
        int genericLevel = 1;

        // Search the matching closing > as challenge to reduce the level back to 0
        // All encountered types must be allowed inside generics
        Iterator<TokenType> previewIter = tokens.peekTypeStream().iterator();
        while (previewIter.hasNext()) {
            TokenType previewTokenType = previewIter.next();

            // Parenthesis not allowed in 5 < Foo.<>foo()
            if (!typesAllowedInGenerics.contains(previewTokenType)) {
                break;
            }

            if (previewTokenType == TokenType.LESS_THAN) {
                genericLevel++;
            } else if (previewTokenType == TokenType.GREATER_THAN) {
                genericLevel--;
            }

            if (genericLevel <= 0) {
                // Managed to reduce the level back to 0 without
                // visiting a forbidden type
                return true;
            }
        }

        // End of code or encountered bad type
        return false;
    }

    boolean isStartOfIndexedForLoop(TokenType tokenType) {
        if (tokenType != TokenType.FOR) {
            return false;
        }

        // Check the next significant tokens, one must be a colon
        // for (int x : values)
        // 1 -> (
        // 2 -> int
        // 3 -> x
        // 4 -> :
        Set<TokenType> ignoreTypes = Set.of(TokenType.ANNOTATION, TokenType.FINAL,
                TokenType.MULTI_LINE_COMMENT, TokenType.SINGLE_LINE_COMMENT);
        return tokens.peekTypeStream()
            .filter(Predicate.not(ignoreTypes::contains))
            .limit(4)
            .anyMatch(TokenType.COLON::equals);
    }

    String patchMultiLineComment(String content, String indent) {
        List<String> lines = content.lines().toList();
        if (lines.size() <= 1) {
            // Nothing to patch for one-line multi-line comments, such as /* foo */
            return content;
        }

        // All other lines need indent, and an extra space
        /*
         * Example multi-line comment
         */
        String firstLine = lines.get(0);
        List<String> otherLines = lines.subList(1, lines.size());

        String otherLinesText = otherLines.stream()
            .map(String::strip)
            .map(otherLine -> indent + " " + otherLine)
            .collect(Collectors.joining("\n"));

        return firstLine + "\n" + otherLinesText;
    }
}
