package org.togetherjava.tjbot.formatter.tokenizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LexerTest {
    private Lexer lexer;

    @BeforeEach
    void setUp() {
        lexer = new Lexer();
    }

    private List<TokenType> tokenize(String code) {
        return lexer.tokenize(code).stream().map(Token::type).toList();
    }

    @Test
    void basic() {
        String realCode = """
                int x=5;
                String y =foo("bar");""";

        List<TokenType> expectedTypes = List.of(TokenType.INT, TokenType.WHITESPACE,
                TokenType.IDENTIFIER, TokenType.ASSIGN, TokenType.NUMBER, TokenType.SEMICOLON,
                TokenType.WHITESPACE, TokenType.IDENTIFIER, TokenType.WHITESPACE,
                TokenType.IDENTIFIER, TokenType.WHITESPACE, TokenType.ASSIGN, TokenType.IDENTIFIER,
                TokenType.OPEN_PARENTHESIS, TokenType.STRING, TokenType.CLOSE_PARENTHESIS,
                TokenType.SEMICOLON);

        List<TokenType> actualTypes = tokenize(realCode);

        assertEquals(expectedTypes, actualTypes);
    }

    @Test
    void emptyCode() {
        String emptyCode = "";

        List<Token> tokens = lexer.tokenize(emptyCode);

        assertTrue(tokens.isEmpty());
    }

    @Test
    @DisplayName("Keywords can be used as identifier if a letter follows them, such as new vs newText")
    void keywordVersusIdentifier() {
        String code = "new newText";
        List<TokenType> expectedTypes =
                List.of(TokenType.NEW, TokenType.WHITESPACE, TokenType.IDENTIFIER);

        List<TokenType> actualTypes = tokenize(code);

        assertEquals(expectedTypes, actualTypes);
    }

    @ParameterizedTest
    @EnumSource(TokenType.class)
    @DisplayName("Each token must be recognized by the expected content and not hidden by another token that prefixes it.")
    void typesAreNotHidden(TokenType expectedTokenType) {
        String text = expectedTokenType.getContentExample();
        TokenType actualTokenType = tokenize(text).getFirst();

        assertEquals(expectedTokenType, actualTokenType, "Tested on: " + text);
    }

    @Test
    @DisplayName("Nested generics with multi-close > must be identified as GREATER.")
    void nestedGenericsMultiCloseGreater() {
        String code = "List<List<Foo>>";
        List<TokenType> expectedTypes = List.of(TokenType.IDENTIFIER, TokenType.LESS_THAN,
                TokenType.IDENTIFIER, TokenType.LESS_THAN, TokenType.IDENTIFIER,
                TokenType.GREATER_THAN, TokenType.GREATER_THAN);

        List<TokenType> actualTypes = tokenize(code);

        assertEquals(expectedTypes, actualTypes);
    }
}
