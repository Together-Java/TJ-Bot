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
        List<TokenType> expectedTypes =
                List.of(TokenType.NEW, TokenType.WHITESPACE, TokenType.IDENTIFIER);

        List<TokenType> actualTypes = tokenize("new newText");

        assertEquals(expectedTypes, actualTypes);
    }

    @ParameterizedTest
    @EnumSource(TokenType.class)
    @DisplayName("Each token must be recognized by the expected content and not hidden by another token that prefixes it.")
    void typesAreNotHidden(TokenType expectedTokenType) {
        String text = provideExampleContent(expectedTokenType);
        TokenType actualTokenType = tokenize(text).get(0);

        assertEquals(expectedTokenType, actualTokenType, "Tested on: " + text);
    }

    private static String provideExampleContent(TokenType tokenType) {
        // TODO Do something about the duplication
        return switch (tokenType) {
            case CLASS -> "class";
            case ENUM -> "enum";
            case RECORD -> "record";
            case INTERFACE -> "interface";
            case IMPORT -> "import";
            case NULL -> "null";
            case EXTENDS -> "extends";
            case IMPLEMENTS -> "implements";
            case NEW -> "new";
            case RETURN -> "return";
            case PACKAGE -> "package";
            case THIS -> "this";
            case YIELD -> "yield";
            case SUPER -> "super";
            case ASSERT -> "assert";
            case CONST -> "const";
            case DEFAULT -> "default";
            case FINALLY -> "finally";
            case THROWS -> "throws";
            case THROW -> "throw";
            case PUBLIC -> "public";
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case STATIC -> "static";
            case SEALED -> "sealed";
            case NON_SEALED -> "non-sealed";
            case FINAL -> "final";
            case ABSTRACT -> "abstract";
            case NATIVE -> "native";
            case STRICTFP -> "strictfp";
            case SYNCHRONIZED -> "synchronized";
            case TRANSIENT -> "transient";
            case VOLATILE -> "volatile";
            case VOID -> "void";
            case INT -> "int";
            case LONG -> "long";
            case SHORT -> "short";
            case BYTE -> "byte";
            case BOOLEAN -> "boolean";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case CHAR -> "char";
            case IF -> "if";
            case ELSE_IF -> "else if";
            case ELSE -> "else";
            case FOR -> "for";
            case WHILE -> "while";
            case DO -> "do";
            case BREAK -> "break";
            case CONTINUE -> "continue";
            case SWITCH -> "switch";
            case CASE -> "case";
            case TRY -> "try";
            case CATCH -> "catch";
            case GOTO -> "goto";
            case OPEN_PARENTHESIS -> "(";
            case CLOSE_PARENTHESIS -> ")";
            case OPEN_BRACES -> "{";
            case CLOSE_BRACES -> "}";
            case OPEN_BRACKETS -> "[";
            case CLOSE_BRACKETS -> "]";
            case DOT -> ".";
            case SEMICOLON -> ";";
            case METHOD_REFERENCE -> "::";
            case COMMA -> ",";
            case NOT -> "!";
            case QUESTION_MARK -> "?";
            case SINGLE_LINE_COMMENT -> "// foo";
            case MULTI_LINE_COMMENT -> """
                    /*
                     * foo
                     * bar
                     */""";
            case SMART_AND -> "&&";
            case SMART_OR -> "||";
            case PLUS_EQUALS -> "+=";
            case MINUS_EQUALS -> "-=";
            case MULTIPLY_EQUALS -> "*=";
            case DIVIDE_EQUALS -> "/=";
            case MODULO_EQUALS -> "%=";
            case AND_EQUALS -> "&=";
            case XOR_EQUALS -> "^=";
            case OR_EQUALS -> "|=";
            case LEFT_SHIFT_EQUALS -> "<<=";
            case LOGICAL_RIGHT_SHIFT_EQUALS -> ">>>=";
            case ARITHMETIC_RIGHT_SHIFT_EQUALS -> ">>=";
            case EQUALS -> "==";
            case NOT_EQUALS -> "!=";
            case GREATER_THAN_OR_EQUALS -> ">=";
            case LOGICAL_RIGHT_SHIFT -> ">>>";
            case ARITHMETIC_RIGHT_SHIFT -> ">>";
            case GREATER_THAN -> ">";
            case LESS_THAN_OR_EQUALS -> "<=";
            case LEFT_SHIFT -> "<<";
            case LESS_THAN -> "<";
            case SINGLE_OR -> "|";
            case SINGLE_AND -> "&";
            case XOR -> "^";
            case ASSIGN -> "=";
            case ARROW -> "->";
            case PLUS_PLUS -> "++";
            case PLUS -> "+";
            case MINUS_MINUS -> "--";
            case MINUS -> "-";
            case DIVIDE -> "/";
            case MULTIPLY -> "*";
            case COLON -> ":";
            case COMPLEMENT -> "~";
            case MODULO -> "%";
            case INSTANCE_OF -> "instanceof";
            case ANNOTATION -> "@Foo";
            case NUMBER -> "1_23.4_56F";
            case STRING -> "\"foo\"";
            case IDENTIFIER -> "foo";
            case WHITESPACE -> " ";
            case UNKNOWN -> "°";
        };
    }
}
