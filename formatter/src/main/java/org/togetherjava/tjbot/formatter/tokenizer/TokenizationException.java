package org.togetherjava.tjbot.formatter.tokenizer;

/**
 * Exception that can occur when lexing
 */
public class TokenizationException extends RuntimeException {
    public TokenizationException(String message) {
        super(message);
    }
}
