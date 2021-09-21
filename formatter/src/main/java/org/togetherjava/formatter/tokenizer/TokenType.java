package org.togetherjava.formatter.tokenizer;

import java.util.regex.Pattern;

/**
 * Represents every possible token that can be lexed by the lexer
 *
 * @author illuminator3
 */
public enum TokenType {
    // keywords
    CLASS("class", true, false),
    ENUM("enum", true, false),
    RECORD("record", true, false),
    INTERFACE("interface", true, false),
    IMPORT("import", true, false),
    IF("if", true, false),
    FOR("for", true, false),
    WHILE("while", true, false),
    NULL("null", true, false),
    EXTENDS("extends", true, false),
    IMPLEMENTS("implements", true, false),
    NEW("new", true, false),
    RETURN("return", true, false),
    PACKAGE("package", true, false),
    THIS("this", true, false),
    YIELD("yield", true, false),
    CATCH("catch", true, false),
    TRY("try", true, false),
    ELSE_IF("else if", true, false), // not really a keyword but required for formatting
    ELSE("else", true, false),
    SUPER("super", true, false),
    // access modifiers
    PUBLIC("public", true, false),
    PRIVATE("private", true, false),
    PROTECTED("protected", true, false),
    STATIC("static", true, false),
    SEALED("sealed", true, false),
    NON_SEALED("non-sealed", true, false),
    FINAL("final", true, false),
    ABSTRACT("abstract", true, false),
    // primitive types
    VOID("void", true, false),
    INT("int", true, false),
    LONG("long", true, false),
    SHORT("short", true, false),
    BYTE("byte", true, false),
    BOOLEAN("boolean", true, false),
    FLOAT("float", true, false),
    DOUBLE("double", true, false),
    CHAR("char", true, false),
    // general tokens
    OPEN_PARENTHESIS("("),
    CLOSE_PARENTHESIS(")"),
    OPEN_BRACES("{"),
    CLOSE_BRACES("}"),
    OPEN_BRACKETS("["),
    CLOSE_BRACKETS("]"),
    SEMICOLON(";"),
    METHOD_REFERENCE("::"),
    COLON(":", false, true), // technically not a "real" operator but used in an enhanced for loop
    COMMA(","),
    ARROW("->", false, true), // used in lambdas
                              // I also wouldn't count this as an operator but again, it's required
                              // for formatting
    PLUS_EQUALS("+=", false, true),
    MINUS_EQUALS("-=", false, true),
    MULTIPLY_EQUALS("*=", false, true),
    DIVIDE_EQUALS("/=", false, true),
    MODULO_EQUALS("%=", false, true),
    AND_EQUALS("&=", false, true),
    OR_EQUALS("|=", false, true),
    EQUALS("==", false, true),
    NOT_EQUALS("!=", false, true),
    GREATER_THAN_OR_EQUALS(">=", false, true),
    GREATER_THAN(">", false, true),
    LESS_THAN_OR_EQUALS("<=", false, true),
    LESS_THAN("<", false, true),
    NOT("!"),
    ASSIGN("=", false, true),
    PLUSPLUS("++"),
    PLUS("+", false, true),
    MINUSMINUS("--"),
    MINUS("-", false, true),
    BOOL_AND("&&", false, true),
    BOOL_OR("||", false, true),

    // this has to be over DIVIDE or else it'll be seen as 2x DIVIDE
    COMMENT(Pattern.compile("^(//.*|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/)")),

    DIVIDE("/", false, true),
    MULTIPLY("*", false, true),
    DOT("."),
    // other
    WILDCARD("?"),
    ANNOTATION(Pattern.compile("^(@[a-zA-Z][a-zA-Z0-9_]*)")),
    NUMBER(Pattern.compile("^((0[xb])?([0-9_]+|[0-9_]*(\\.\\d*))[dDfFlL]?)")),
    STRING(Pattern.compile("^(\"[^\"]*\")")),
    IDENTIFIER(Pattern.compile("^([a-zA-Z][a-zA-Z0-9_]*)")),
    WHITESPACE(Pattern.compile("^(\s+)")),
    UNKNOWN(Pattern.compile("^(.)"));

    private final Pattern regex;
    private final boolean isKeyword;
    private final boolean isOperator;

    TokenType(String pattern, boolean isKeyword, boolean isOperator) {
        if (!isKeyword) {
            this.regex = Pattern.compile("^(" + Pattern.quote(pattern) + ")");
        } else {
            this.regex = Pattern.compile("^(" + Pattern.quote(pattern) + ")(?![a-zA-Z])");
        }

        this.isKeyword = isKeyword;
        this.isOperator = isOperator;
    }

    TokenType(Pattern regex) {
        this.regex = regex;
        this.isKeyword = false;
        this.isOperator = false;
    }

    TokenType(String spattern) {
        this(spattern, false, false);
    }

    public Pattern getRegex() {
        return regex;
    }

    public boolean isKeyword() {
        return isKeyword;
    }

    public boolean isOperator() {
        return isOperator;
    }
}
