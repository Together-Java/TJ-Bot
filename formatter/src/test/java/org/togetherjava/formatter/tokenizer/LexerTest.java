package org.togetherjava.formatter.tokenizer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LexerTest {
    Lexer lexer;

    @BeforeAll
    void init() {
        lexer = new Lexer();
    }

    @Test
    @DisplayName("Invalid input does throw an exception")
    void testInvalidInput() {
        assertThrows(TokenizationException.class, () -> lexer.tokenize("\r"));
    }

    @Test
    @DisplayName("Valid input does not throw an exception")
    void testValidInput() {
        assertDoesNotThrow(() -> lexer.tokenize("\n"));
    }

    @Test
    @DisplayName("Empty input does not throw an exception")
    void testEmptyInput() {
        assertDoesNotThrow(() -> lexer.tokenize(""));
    }

    @Test
    @DisplayName("Given input returns expected result")
    void testInput() {
        String testString =
            "class enum record interface import if for while null extends implements new return package this yield catch try else if else public private protected static sealed non-sealed final abstract void int long short byte boolean float double char ( ) { } [ ] ; :: : , += -= *= /= %= &= |= == != >= > <= < ! = ++ + -- - && || /* a */ / * . @annotation 0 \"string\" identifier ` ";
        List<TokenType> tokens = lexer.tokenize(testString).stream().map(Token::type).toList(),
                        expected = new ArrayList<>();

        for (TokenType type : TokenType.values()) {
            if (type == TokenType.UNKNOWN) {
                System.out.println("hi");
            }

            if (type != TokenType.WHITSPACE) {
                expected.add(type);
                expected.add(TokenType.WHITSPACE);
            }
        }

        assertEquals(expected, tokens);
    }
}
