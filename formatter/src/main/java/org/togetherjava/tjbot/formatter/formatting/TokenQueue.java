package org.togetherjava.tjbot.formatter.formatting;

import org.togetherjava.tjbot.formatter.tokenizer.Token;
import org.togetherjava.tjbot.formatter.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Queue that holds tokens to be consumed.
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
     */
    Token consume() {
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
     */
    TokenType peekType() {
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
     */
    TokenType peekTypeBack() {
        return tokens.get(nextTokenIndex - 1).type();
    }

    /**
     * Peeks at the type of the next tokens, without consuming them. Must only be used if
     * {@link #isEmpty()} returns {@code false}.
     * <p>
     * This essentially gives a stream for all remaining tokens in the queue.
     * 
     * @return the next tokens types
     */
    Stream<TokenType> peekTypeStream() {
        return tokens.subList(nextTokenIndex, tokens.size()).stream().map(Token::type);
    }

    /**
     * Peeks at the type of the previous tokens, without changing the queue. Must only be used after
     * {@link #consume()} ()} has been used at least once.
     * <p>
     * This essentially gives a stream for all already consumed tokens in the queue. The stream is
     * ordered from the most recently consumed token to the first consumed token.
     * 
     * @return the previous tokens types
     */
    Stream<TokenType> peekBackStream() {
        return IntStream.range(0, nextTokenIndex)
            .map(i -> nextTokenIndex - i - 1)
            .mapToObj(tokens::get)
            .map(Token::type);
    }
}
