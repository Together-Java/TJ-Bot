package org.togetherjava.tjbot.features.chatgpt;

import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;

/**
 * Logical abstraction over OpenAI chat models.
 * <p>
 * This enum allows the application to select models and reasoning efforts based on
 * performance/quality intent rather than hard-coding specific OpenAI model versions throughout the
 * codebase.
 *
 */
public enum ChatGptModel {
    /**
     * Fastest response time with the lowest computational cost.
     */
    FASTEST(ChatModel.GPT_6_LUNA, ReasoningEffort.NONE),

    /**
     * Balanced option between speed and quality.
     */
    FAST(ChatModel.GPT_6_LUNA, ReasoningEffort.LOW),

    /**
     * Highest quality responses with increased reasoning capability.
     */
    HIGH_QUALITY(ChatModel.GPT_6_LUNA, ReasoningEffort.MEDIUM);

    private final ChatModel chatModel;
    private final ReasoningEffort reasoningEffort;

    ChatGptModel(ChatModel chatModel, ReasoningEffort reasoningEffort) {
        this.chatModel = chatModel;
        this.reasoningEffort = reasoningEffort;
    }

    /**
     * @return the underlying OpenAI model used by this enum.
     */
    public ChatModel toChatModel() {
        return chatModel;
    }

    /**
     * @return the reasoning effort used by this enum.
     */
    public ReasoningEffort toReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * @return the reasoning used by this enum.
     */
    public Reasoning toReasoning() {
        return Reasoning.builder().effort(reasoningEffort).build();
    }

    @Override
    public String toString() {
        return chatModel.toString();
    }
}
