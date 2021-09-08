package org.togetherjava.formatter.tokenizer;

import java.util.regex.Pattern;

public enum TokenType {
    // keywords
    CLASS("class"),
    ENUM("enum"),
    RECORD("record"),
    INTERFACE("interface"),
    IMPORT("import"),
    IF("if"),
    FOR("for"),
    WHILE("while"),
    NULL("null"),
    EXTENDS("extends"),
    IMPLEMENTS("implements"),
    NEW("new"),
    RETURN("return"),
    PACKAGE("package"),
    THIS("this"),
    // access modifiers
    PUBLIC("public"),
    PRIVATE("private"),
    PROTECTED("protected"),
    STATIC("static"),
    SEALED("sealed"),
    NON_SEALED("non-sealed"),
    FINAL("final"),
    ABSTRACT("abstract"),
    // primitive types
    VOID("void"),
    INT("int"),
    LONG("long"),
    SHORT("short"),
    BYTE("byte"),
    BOOLEAN("boolean"),
    FLOAT("float"),
    DOUBLE("double"),
    CHAR("char"),
    // general tokens
    OPEN_PARENTHESIS("("),
    CLOSE_PARANTHESIS(")"),
    OPEN_BRACES("{"),
    CLOSE_BRACES("}"),
    OPEN_BRACKETS("["),
    CLOSE_BRACKETS("]"),
    OPEN_DIAMOND("<"),
    CLOSE_DIAMOND(">"),
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
    PLUS("+"),
    MINUS("-"),
    DIVIDE("/"),
    MULTIPLY("*"),
    DOT("."),
    // other
    ANNOTATION(Pattern.compile("^@[a-zA-Z][a-zA-Z0-9_]*")),
    NUMBER(Pattern.compile("^(0[xb])?\\d+[dDfFlL]?")),
    STRING(Pattern.compile("^\"[^\"]*\"")),
    IDENTIFIER(Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*")),
    WHITSPACE(Pattern.compile("^\s+")),
    UNKNOWN(Pattern.compile("^."));

    private final Pattern regex;

    TokenType(Pattern regex) {
        this.regex = regex;
    }

    TokenType(String spattern) {
        this(Pattern.compile("^\\Q" + spattern + "\\E"));
    }

    public Pattern getRegex() {
        return regex;
    }
}
