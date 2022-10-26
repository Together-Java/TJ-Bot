package org.togetherjava.tjbot.formatter.formatting;

import org.togetherjava.tjbot.formatter.tokenizer.Token;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// TODO Adjust to actual needs
final class TokenQueue {
    private final List<Token> tokens;
    private int nextTokenIndex;

    TokenQueue(Collection<Token> tokens) {
        this.tokens = new ArrayList<>(tokens);
    }

    boolean isEmpty() {
        return nextTokenIndex >= tokens.size();
    }

    int remainingSize() {
        return tokens.size() - nextTokenIndex;
    }

    Token consume() {
        Token token = tokens.get(nextTokenIndex);
        nextTokenIndex++;
        return token;
    }

    Token peek() {
        return tokens.get(nextTokenIndex);
    }

    Token peekBack() {
        return tokens.get(nextTokenIndex - 1);
    }

    Stream<Token> peekStream() {
        return tokens.subList(nextTokenIndex, tokens.size()).stream();
    }

    Stream<Token> peekBackStream() {
        return IntStream.range(0, nextTokenIndex)
            .map(i -> nextTokenIndex - i - 1)
            .mapToObj(tokens::get);
    }
}
