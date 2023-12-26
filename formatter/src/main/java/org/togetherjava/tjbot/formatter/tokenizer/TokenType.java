package org.togetherjava.tjbot.formatter.tokenizer;

import java.nio.CharBuffer;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * All types of tokens recognized by {@link Lexer}.
 * <p>
 * Token types can be used to find the next matching token (see {@link #matches(CharSequence)}). For
 * example, for the code {@code int x = "foo"}, only {@link #INT} would match. Other participating
 * tokens, such as {@link #IDENTIFIER} or {@link #STRING} do not match yet.
 */
// Sonar wants Javadoc on all tokens, but they are self-explanatory based
// on the enum description and context.
@SuppressWarnings("squid:S1176")
public enum TokenType {
    // NOTE The entries have an order-dependency by design.
    // Their patterns are applied from top to bottom. So entries that are not prefix-free
    // like "--" and "-" must be ordered with "--" before "-" to correctly match.

    // Keywords
    CLASS("class", Attribute.KEYWORD),
    ENUM("enum", Attribute.KEYWORD),
    RECORD("record", Attribute.KEYWORD),
    INTERFACE("interface", Attribute.KEYWORD),
    IMPORT("import", Attribute.KEYWORD),
    NULL("null", Attribute.KEYWORD),
    EXTENDS("extends", Attribute.KEYWORD),
    IMPLEMENTS("implements", Attribute.KEYWORD),
    NEW("new", Attribute.KEYWORD),
    RETURN("return", Attribute.KEYWORD),
    PACKAGE("package", Attribute.KEYWORD),
    THIS("this", Attribute.KEYWORD),
    YIELD("yield", Attribute.KEYWORD),
    SUPER("super", Attribute.KEYWORD),
    ASSERT("assert", Attribute.KEYWORD),
    CONST("const", Attribute.KEYWORD),
    DEFAULT("default", Attribute.KEYWORD),
    FINALLY("finally", Attribute.KEYWORD),
    THROWS("throws", Attribute.KEYWORD),
    THROW("throw", Attribute.KEYWORD),

    // Access modifiers
    PUBLIC("public", Attribute.KEYWORD),
    PRIVATE("private", Attribute.KEYWORD),
    PROTECTED("protected", Attribute.KEYWORD),
    STATIC("static", Attribute.KEYWORD),
    SEALED("sealed", Attribute.KEYWORD),
    NON_SEALED("non-sealed", Attribute.KEYWORD),
    FINAL("final", Attribute.KEYWORD),
    ABSTRACT("abstract", Attribute.KEYWORD),
    NATIVE("native", Attribute.KEYWORD),
    STRICTFP("strictfp", Attribute.KEYWORD),
    SYNCHRONIZED("synchronized", Attribute.KEYWORD),
    TRANSIENT("transient", Attribute.KEYWORD),
    VOLATILE("volatile", Attribute.KEYWORD),

    // Primitives
    VOID("void", Attribute.KEYWORD),
    INT("int", Attribute.KEYWORD),
    LONG("long", Attribute.KEYWORD),
    SHORT("short", Attribute.KEYWORD),
    BYTE("byte", Attribute.KEYWORD),
    BOOLEAN("boolean", Attribute.KEYWORD),
    FLOAT("float", Attribute.KEYWORD),
    DOUBLE("double", Attribute.KEYWORD),
    CHAR("char", Attribute.KEYWORD),

    // Flow control
    IF("if", Attribute.KEYWORD),
    // Technically two keywords, but we want it to stick together when formatting
    ELSE_IF("else if", Attribute.KEYWORD),
    ELSE("else", Attribute.KEYWORD),
    FOR("for", Attribute.KEYWORD),
    WHILE("while", Attribute.KEYWORD),
    DO("do", Attribute.KEYWORD),
    BREAK("break", Attribute.KEYWORD),
    CONTINUE("continue", Attribute.KEYWORD),
    SWITCH("switch", Attribute.KEYWORD),
    CASE("case", Attribute.KEYWORD),
    TRY("try", Attribute.KEYWORD),
    CATCH("catch", Attribute.KEYWORD),
    GOTO("goto", Attribute.KEYWORD),

    // Braces
    OPEN_PARENTHESIS("("),
    CLOSE_PARENTHESIS(")"),
    OPEN_BRACES("{"),
    CLOSE_BRACES("}"),
    OPEN_BRACKETS("["),
    CLOSE_BRACKETS("]"),

    // General
    DOT("."),
    SEMICOLON(";"),
    METHOD_REFERENCE("::"),
    COMMA(","),
    QUESTION_MARK("?", Attribute.BINARY_OPERATOR),

    // Comments
    SINGLE_LINE_COMMENT(Pattern.compile("//.*(?=\n|$)"), "// Foo"),
    MULTI_LINE_COMMENT(Pattern.compile("""
            /\\* #Start
            .* #Content
            \\*/ #End
            """, Pattern.DOTALL | Pattern.COMMENTS), "/* Foo */"),

    // Operators
    // NOTE right shifts (<<, >>, >>>) are intentionally left out
    // they conflict with nested generics like List<List<Foo>>
    SMART_AND("&&", Attribute.BINARY_OPERATOR),
    SMART_OR("||", Attribute.BINARY_OPERATOR),
    PLUS_EQUALS("+=", Attribute.BINARY_OPERATOR),
    MINUS_EQUALS("-=", Attribute.BINARY_OPERATOR),
    MULTIPLY_EQUALS("*=", Attribute.BINARY_OPERATOR),
    DIVIDE_EQUALS("/=", Attribute.BINARY_OPERATOR),
    MODULO_EQUALS("%=", Attribute.BINARY_OPERATOR),
    AND_EQUALS("&=", Attribute.BINARY_OPERATOR),
    XOR_EQUALS("^=", Attribute.BINARY_OPERATOR),
    OR_EQUALS("|=", Attribute.BINARY_OPERATOR),
    LEFT_SHIFT_EQUALS("<<=", Attribute.BINARY_OPERATOR),
    LOGICAL_RIGHT_SHIFT_EQUALS(">>>=", Attribute.BINARY_OPERATOR),
    ARITHMETIC_RIGHT_SHIFT_EQUALS(">>=", Attribute.BINARY_OPERATOR),
    EQUALS("==", Attribute.BINARY_OPERATOR),
    NOT_EQUALS("!=", Attribute.BINARY_OPERATOR),
    NOT("!"),
    GREATER_THAN_OR_EQUALS(">=", Attribute.BINARY_OPERATOR),
    GREATER_THAN(">", Attribute.BINARY_OPERATOR),
    LESS_THAN_OR_EQUALS("<=", Attribute.BINARY_OPERATOR),
    LEFT_SHIFT("<<", Attribute.BINARY_OPERATOR),
    LESS_THAN("<", Attribute.BINARY_OPERATOR),
    SINGLE_OR("|", Attribute.BINARY_OPERATOR),
    SINGLE_AND("&", Attribute.BINARY_OPERATOR),
    XOR("^", Attribute.BINARY_OPERATOR),
    ASSIGN("=", Attribute.BINARY_OPERATOR),
    // Technically not an operator, but used like one in lambdas and switch expressions
    ARROW("->", Attribute.BINARY_OPERATOR),
    PLUS_PLUS("++", Attribute.UNARY_OPERATOR),
    PLUS("+", Attribute.BINARY_OPERATOR),
    MINUS_MINUS("--", Attribute.UNARY_OPERATOR),
    MINUS("-", Attribute.BINARY_OPERATOR),
    DIVIDE("/", Attribute.BINARY_OPERATOR),
    MULTIPLY("*", Attribute.BINARY_OPERATOR),
    // Technically not an operator, but used like one in enhanced-for
    COLON(":", Attribute.BINARY_OPERATOR),
    COMPLEMENT("~", Attribute.UNARY_OPERATOR),
    MODULO("%", Attribute.BINARY_OPERATOR),
    INSTANCE_OF("instanceof", Attribute.BINARY_OPERATOR),

    // Other
    ANNOTATION(Pattern.compile("@[a-zA-Z]\\w*"), "@Foo"),
    NUMBER(Pattern.compile("""
            (0[xb])? #Different base
            ([\\d_]+ #Integers
            | [\\d_]+\\.[\\d_]+ #Float with both, 1.3
            | [\\d_]+\\. #Float only left, 1.
            | \\.[\\d_]+ #Float only right, .1
            )
            [dDfFlL]? #Type suffix
            """, Pattern.COMMENTS), "1_23.4_56F"),
    STRING(Matching::matchesString, Attribute.NONE, "\"foo\""),
    IDENTIFIER(Pattern.compile("[a-zA-Z]\\w*"), "foo"),
    WHITESPACE(Pattern.compile("\\s+"), " "),

    // Fallback for everything that has not been matched yet
    UNKNOWN(Pattern.compile(".", Pattern.DOTALL), "°");

    private final Function<CharSequence, Optional<String>> matcher;
    private final Attribute attribute;
    private final String contentExample;

    /**
     * Gets all token types in the order they should be used for matching.
     * <p>
     * This is important, since not all types match prefix-free. For example, {@link #DIVIDE} hides
     * {@link #SINGLE_LINE_COMMENT} if matched before.
     *
     * @return all token types in match order
     */
    public static TokenType[] getAllInMatchOrder() {
        // We have an order-dependency by design here
        return TokenType.values();
    }

    TokenType(Function<CharSequence, Optional<String>> matcher, Attribute attribute,
            String contentExample) {
        this.matcher = matcher;
        this.attribute = attribute;
        this.contentExample = contentExample;

        requireMatchesExample();
    }

    private void requireMatchesExample() {
        if (matcher.apply(contentExample).isEmpty()) {
            throw new AssertionError(
                    "The given content example (%s) is not matched by the token type (%s)"
                        .formatted(contentExample, this));
        }
    }

    TokenType(Pattern pattern, String contentExample) {
        this(text -> Matching.matchesPattern(pattern, text), Attribute.NONE, contentExample);
    }

    TokenType(String symbol, Attribute attribute) {
        this(text -> Matching.matchesSymbol(symbol, text, attribute), attribute, symbol);
    }

    TokenType(String symbol) {
        this(symbol, Attribute.NONE);
    }

    /**
     * Attempts to match this token type against the given text and returns the matched token.
     * <p>
     * For example, {@code INT.matches("int x = 5")} matches and returns {@code Token("int", INT)}.
     * <p>
     * For performance reasons, this method should ideally be called with an instance of
     * {@link CharSequence} that creates views only, such as {@link CharBuffer}.
     *
     * @param text the text to match against
     * @return the token matched by this type, if any
     */
    public Optional<Token> matches(CharSequence text) {
        return matcher.apply(text).map(content -> new Token(content, this));
    }

    /**
     * The attribute of the type, if it can be further specified.
     *
     * @return the attribute of the type
     */
    public Attribute getAttribute() {
        return attribute;
    }

    /**
     * An example token content this type would match.
     * 
     * @return example match
     */
    String getContentExample() {
        return contentExample;
    }

    /**
     * Attributes of token types.
     * <p>
     * For example, a token like {@code +} is a {@link #BINARY_OPERATOR}.
     */
    public enum Attribute {
        /**
         * A keyword in the language, has to stand alone, for example with a space left and right.
         * Such as in {@code public final class}.
         */
        KEYWORD,
        /**
         * An operator with two arguments, one left and one right. For example {@code 5 + 3}.
         */
        BINARY_OPERATOR,
        /**
         * An operator with a single argument, for example {@code x++}.
         */
        UNARY_OPERATOR,
        /**
         * No further specification of the type.
         */
        NONE
    }
}
