package org.togetherjava.formatter.tokenizer;

import java.util.regex.Pattern;

public enum TokenType {
    // keywords
    CLASS("class", true),
    ENUM("enum", true),
    RECORD("record", true),
    INTERFACE("interface", true),
    IMPORT("import", true),
    IF("if", true),
    FOR("for", true),
    WHILE("while", true),
    NULL("null", true),
    EXTENDS("extends", true),
    IMPLEMENTS("implements", true),
    NEW("new", true),
    RETURN("return", true),
    PACKAGE("package", true),
    THIS("this", true),
    YIELD("yield", true),
    CATCH("catch", true),
    TRY("try", true),
    // access modifiers
    PUBLIC("public", true),
    PRIVATE("private", true),
    PROTECTED("protected", true),
    STATIC("static", true),
    SEALED("sealed", true),
    NON_SEALED("non-sealed", true),
    FINAL("final", true),
    ABSTRACT("abstract", true),
    // primitive types
    VOID("void", true),
    INT("int", true),
    LONG("long", true),
    SHORT("short", true),
    BYTE("byte", true),
    BOOLEAN("boolean", true),
    FLOAT("float", true),
    DOUBLE("double", true),
    CHAR("char", true),
    // general tokens
    OPEN_PARENTHESIS("("),
    CLOSE_PARENTHESIS(")"),
    OPEN_BRACES("{"),
    CLOSE_BRACES("}"),
    OPEN_BRACKETS("["),
    CLOSE_BRACKETS("]"),
    SEMICOLON(";"),
    COLON(":"),
    COMMA(","),
    EQUALS("=="),
    NOT_EQUALS("!="),
    BIGGER_OR_EQUALS(">="),
    BIGGER(">"),
    SMALLER_OR_EQUALS("<="),
    SMALLER("<"),
    NOT("!"),
    ASSIGN("="),
    PLUSPLUS("++"),
    PLUS("+"),
    MINUSMINUS("--"),
    MINUS("-"),

    // this has to be other DIVIDE or else it'll be seen as two divides
    COMMENT(Pattern.compile("^(//.*)")),

    DIVIDE("/"),
    MULTIPLY("*"),
    DOT("."),
    // other
    ANNOTATION(Pattern.compile("^(@[a-zA-Z][a-zA-Z0-9_]*)")),
    NUMBER(Pattern.compile("^((0[xb])?([0-9_]+|[0-9_]*(\\.\\d*))[dDfFlL]?)")),
    STRING(Pattern.compile("^(\"[^\"]*\")")),
    IDENTIFIER(Pattern.compile("^([a-zA-Z][a-zA-Z0-9_]*)")),
    WHITSPACE(Pattern.compile("^(\s+)")),
    UNKNOWN(Pattern.compile("^(.)"));

    private final Pattern regex;
    private final boolean isKeyword;

    TokenType(String spattern, boolean isKeyword) {
        if (!isKeyword) {
            this.regex = Pattern.compile("^(\\Q" + spattern + "\\E)");
        } else {
            this.regex = Pattern.compile("^(\\Q" + spattern + "\\E)[^a-z]");
        }

        this.isKeyword = isKeyword;
    }

    TokenType(Pattern regex) {
        this.regex = regex;
        this.isKeyword = false;
    }

    TokenType(String spattern) {
//        this(Pattern.compile("^(\\Q" + spattern + "\\E)"));
        this(spattern, false);
    }

    public Pattern getRegex() {
        return regex;
    }

    public boolean isKeyword() {
        return isKeyword;
    }
}
