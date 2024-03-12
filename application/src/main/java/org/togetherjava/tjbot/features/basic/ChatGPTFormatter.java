package org.togetherjava.tjbot.features.basic;

import org.togetherjava.tjbot.features.chatgpt.ChatGptService;

import java.util.Optional;

/**
 * A utility class for formatting code using ChatGPT.
 */
public final class ChatGPTFormatter {
    private final ChatGptService chatGptService;

    /**
     * Constructs a new {@link ChatGPTFormatter} with the provided {@link ChatGptService}.
     *
     * @param chatGptService the {@link ChatGptService} used for formatting code
     */
    public ChatGPTFormatter(ChatGptService chatGptService) {
        this.chatGptService = chatGptService;
    }

    /**
     * Formats the provided code using ChatGPT.
     *
     * @param code the code to be formatted
     * @return an Optional containing the formatted code if successful, empty otherwise
     */
    public Optional<String> format(CharSequence code) {
        return chatGptService.formatCode(code);
    }
}
