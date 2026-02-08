package org.togetherjava.tjbot.features.chatgpt;

import com.openai.models.ChatModel;

/**
 * Logical abstraction over OpenAI chat models.
 * <p>
 * This enum allows the application to select models based on performance/quality intent rather than
 * hard-coding specific OpenAI model versions throughout the codebase.
 *
 */
public enum ChatGptModel {
    /**
     * Fastest response time with the lowest computational cost.
     */
    FASTEST(ChatModel.GPT_3_5_TURBO),

    /**
     * Balanced option between speed and quality.
     */
    FAST(ChatModel.GPT_4_1_MINI),

    /**
     * Highest quality responses with increased reasoning capability.
     */
    HIGH_QUALITY(ChatModel.GPT_5_MINI);

    private final ChatModel chatModel;

    ChatGptModel(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * @return the underlying OpenAI model used by this enum.
     */
    public ChatModel toChatModel() {
        return chatModel;
    }

    @Override
    public String toString() {
        return chatModel.toString();
    }
}
