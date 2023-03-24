package org.togetherjava.tjbot.features.chaptgpt;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Service used to communicate to OpenAI API to generate responses.
 */
public class ChatGPTService {
    private static final Logger logger = LoggerFactory.getLogger(ChatGPTService.class);
    private static final long TIMEOUT_DURATION = 10000L; // In milliseconds
    private static final int MAX_TOKENS = 3000;
    private final OpenAiService openAiService;

    /**
     * Creates instance of ChatGPTService
     * 
     * @param config Config - needed for token to OpenAI API.
     */
    public ChatGPTService(Config config) {
        String token = config.getOpenaiToken();
        openAiService = new OpenAiService(token, Duration.ofMillis(TIMEOUT_DURATION));
    }

    /**
     * Prompt ChatGPT with a question and receive a response.
     * 
     * @param question String - The question being asked of ChatGPT. Max is 4000 tokens.
     * @see <a href="https://platform.openai.com/docs/guides/chat/managing-tokens">ChatGPT
     *      Tokens</a>.
     * @return response from ChatGPT as a String.
     * @throws OpenAiHttpException - Thrown when an error occurs with the API such as a timeout or
     *         token error such as it being expired or revoked.
     */
    public String ask(String question) {
        try {
            ChatMessage chatMessage =
                    new ChatMessage(ChatMessageRole.USER.value(), Objects.requireNonNull(question));
            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(List.of(chatMessage))
                .frequencyPenalty(0.5)
                .temperature(0.7)
                .maxTokens(MAX_TOKENS)
                .n(1)
                .build();
            return openAiService.createChatCompletion(chatCompletionRequest)
                .getChoices()
                .get(0)
                .getMessage()
                .getContent();
        } catch (OpenAiHttpException openAiHttpException) {
            logger.error(String.format(
                    "There was an error using the OpenAI API: %s Code: %s Type: %s Status Code:%s",
                    openAiHttpException.getMessage(), openAiHttpException.code,
                    openAiHttpException.type, openAiHttpException.statusCode));
        }
        return "An error has occurred while trying to communication with ChatGPT. Please try again later";
    }
}
