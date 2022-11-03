package org.togetherjava.tjbot.formatter.formatting;

import org.togetherjava.tjbot.formatter.tokenizer.Token;
import org.togetherjava.tjbot.formatter.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Queue that holds {@link Token}s to be consumed. Generally, this holds the result of lexing code
 * (see {@link org.togetherjava.tjbot.formatter.tokenizer.Lexer}), then processed by the actual
 * formatter (see {@link CodeSectionFormatter}).
 * <p>
 * The core methods are {@link #consume()} and {@link #isEmpty()}. Further, the queue allows peeking
 * in both directions.
 * <p>
 * The queue does not release tokens upon consumption. To prevent memory leaks, the queue should not
 * be kept alive longer than necessary.
 * <p>
 * The class is not thread-safe.
 */
final class TokenQueue {
    private final List<Token> tokens;
    private int nextTokenIndex;

    /**
     * Creates a new queue that consumes the given tokens. Consumption starts at the beginning of
     * the given collection.
     * <p>
     * The queue is not backed by the collection.
     * 
     * @param tokens to consume by the queue
     */
    TokenQueue(Collection<Token> tokens) {
        this.tokens = new ArrayList<>(tokens);
    }

    /**
     * Whether there are still tokens to be consumed.
     * 
     * @return Whether there are still tokens to be consumed
     */
    boolean isEmpty() {
        return nextTokenIndex >= tokens.size();
    }

    /**
     * The remaining amount of tokens that can still be consumed, i.e. how often {@link #consume()}
     * can still be called.
     * 
     * @return the remaining amount of tokens
     */
    int remainingSize() {
        return tokens.size() - nextTokenIndex;
    }

    /**
     * Consumes the next token. Must only be invoked if {@link #isEmpty()} returns {@code false}.
     * 
     * @return the consumed token
     * @throws NoSuchElementException if the queue is empty
     */
    Token consume() {
        if (isEmpty()) {
            throw new NoSuchElementException("The queue is empty, can not consume another token");
        }
        Token token = tokens.get(nextTokenIndex);
        nextTokenIndex++;
        return token;
    }

    /**
     * Peeks at type of the next token, without consuming it. Must only be used if
     * {@link #isEmpty()} returns {@code false}.
     * <p>
     * That is the type of the token, which would be returned by using {@link #consume()}.
     * 
     * @return the next tokens type
     * @throws NoSuchElementException if the queue is empty
     */
    TokenType peekType() {
        if (isEmpty()) {
            throw new NoSuchElementException("The queue is empty, can not peek another token");
        }
        return tokens.get(nextTokenIndex).type();
    }

    /**
     * Peeks at the type of the previous token, without changing the queue. Must only be used after
     * {@link #consume()} ()} has been used at least once.
     * <p>
     * That is the type of the token, which has been returned by the previous usage of
     * {@link #consume()}.
     * 
     * @return the previous tokens type
     * @throws NoSuchElementException if no token was consumed yet
     */
    TokenType peekTypeBack() {
        if (nextTokenIndex == 0) {
            throw new NoSuchElementException("No token has been consumed yet, can not peek back");
        }
        return tokens.get(nextTokenIndex - 1).type();
    }

    /**
     * Peeks at the type of the next tokens, without consuming them.
     * <p>
     * This essentially gives a stream for all remaining tokens in the queue.
     * 
     * @return the next tokens types, an empty stream if the queue is empty
     */
    Stream<TokenType> peekTypeStream() {
        if (isEmpty()) {
            return Stream.of();
        }
        return tokens.subList(nextTokenIndex, tokens.size()).stream().map(Token::type);
    }

    /**
     * Peeks at the type of the previous tokens, without changing the queue.
     * <p>
     * This essentially gives a stream for all already consumed tokens in the queue. The stream is
     * ordered from the most recently consumed token to the first consumed token.
     * 
     * @return the previous tokens types, an empty stream if no token has been consumed yet
     */
    Stream<TokenType> peekTypeBackStream() {
        if (nextTokenIndex == 0) {
            return Stream.of();
        }

        return IntStream.range(0, nextTokenIndex)
            .map(i -> nextTokenIndex - i - 1)
            .mapToObj(tokens::get)
            .map(Token::type);
    }
}
