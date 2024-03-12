package org.togetherjava.tjbot.features.basic;

import org.togetherjava.tjbot.features.chatgpt.ChatGptService;

import java.util.Optional;

public final class ChatGPTFormatter {
    private final ChatGptService chatGptService;

    public ChatGPTFormatter(ChatGptService chatGptService) {
        this.chatGptService = chatGptService;
    }

    public Optional<String> format(CharSequence code) {
        return chatGptService.formatCode(code);
    }
}
