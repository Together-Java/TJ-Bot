package org.togetherjava.tjbot.formatter.formatting;

import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.formatter.tokenizer.Token;
import org.togetherjava.tjbot.formatter.tokenizer.TokenType;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TokenQueueTest {

    @Test
    void isEmpty() {
        TokenQueue queue = provideEmptyTokenQueue();
        assertTrue(queue.isEmpty());

        queue = provide2TokenQueue();
        assertFalse(queue.isEmpty());

        queue.consume();
        assertFalse(queue.isEmpty());

        queue.consume();
        assertTrue(queue.isEmpty());
    }

    @Test
    void remainingSize() {
        TokenQueue queue = provideEmptyTokenQueue();
        assertEquals(0, queue.remainingSize());

        queue = provide2TokenQueue();
        assertEquals(2, queue.remainingSize());

        queue.consume();
        assertEquals(1, queue.remainingSize());
        queue.consume();
        assertEquals(0, queue.remainingSize());
    }

    @Test
    void consume() {
        TokenQueue queue = provideEmptyTokenQueue();
        assertThrows(NoSuchElementException.class, queue::consume);

        queue = provide2TokenQueue();
        assertEquals("class", queue.consume().content());

        assertEquals("Foo", queue.consume().content());

        assertThrows(NoSuchElementException.class, queue::consume);
    }

    @Test
    void peekType() {
        TokenQueue queue = provideEmptyTokenQueue();
        assertThrows(NoSuchElementException.class, queue::peekType);

        queue = provide2TokenQueue();
        assertEquals(TokenType.CLASS, queue.peekType());
        // Does not consume tokens, still the same
        assertEquals(TokenType.CLASS, queue.peekType());

        queue.consume();
        assertEquals(TokenType.IDENTIFIER, queue.peekType());

        queue.consume();
        assertThrows(NoSuchElementException.class, queue::peekType);
    }

    @Test
    void peekTypeBack() {
        TokenQueue queue = provideEmptyTokenQueue();
        assertThrows(NoSuchElementException.class, queue::peekTypeBack);

        queue = provide2TokenQueue();
        assertThrows(NoSuchElementException.class, queue::peekTypeBack);

        queue.consume();
        assertEquals(TokenType.CLASS, queue.peekTypeBack());
        // Does not consume tokens, still the same
        assertEquals(TokenType.CLASS, queue.peekTypeBack());

        queue.consume();
        assertEquals(TokenType.IDENTIFIER, queue.peekTypeBack());
    }

    @Test
    void peekTypeStream() {
        TokenQueue queue = provideEmptyTokenQueue();
        assertTrue(queue.peekTypeStream().toList().isEmpty());

        queue = provide2TokenQueue();
        List<TokenType> expectedTypes = List.of(TokenType.CLASS, TokenType.IDENTIFIER);
        assertEquals(expectedTypes, queue.peekTypeStream().toList());
        // Does not consume tokens, still the same
        assertEquals(expectedTypes, queue.peekTypeStream().toList());

        queue.consume();
        expectedTypes = List.of(TokenType.IDENTIFIER);
        assertEquals(expectedTypes, queue.peekTypeStream().toList());

        queue.consume();
        assertTrue(queue.peekTypeStream().toList().isEmpty());
    }

    @Test
    void peekTypeBackStream() {
        TokenQueue queue = provideEmptyTokenQueue();
        assertTrue(queue.peekTypeBackStream().toList().isEmpty());

        queue = provide2TokenQueue();
        assertTrue(queue.peekTypeBackStream().toList().isEmpty());

        queue.consume();
        List<TokenType> expectedTypes = List.of(TokenType.CLASS);
        assertEquals(expectedTypes, queue.peekTypeBackStream().toList());
        // Does not consume tokens, still the same
        assertEquals(expectedTypes, queue.peekTypeBackStream().toList());

        queue.consume();
        expectedTypes = List.of(TokenType.IDENTIFIER, TokenType.CLASS);
        assertEquals(expectedTypes, queue.peekTypeBackStream().toList());
    }

    private static TokenQueue provide2TokenQueue() {
        return new TokenQueue(List.of(new Token("class", TokenType.CLASS),
                new Token("Foo", TokenType.IDENTIFIER)));
    }

    private static TokenQueue provideEmptyTokenQueue() {
        return new TokenQueue(List.of());
    }
}
