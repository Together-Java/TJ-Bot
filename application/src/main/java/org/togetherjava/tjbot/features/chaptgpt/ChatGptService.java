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
import java.util.Optional;

/**
 * Service used to communicate to OpenAI API to generate responses.
 */
public class ChatGptService {
    private static final Logger logger = LoggerFactory.getLogger(ChatGptService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_TOKENS = 3_000;
    private static final int RESPONSE_LENGTH_LIMIT = 2_000;
    private boolean isDisabled = false;
    private final OpenAiService openAiService;

    /**
     * Creates instance of ChatGPTService
     * 
     * @param config needed for token to OpenAI API.
     */
    public ChatGptService(Config config) {
        String apiKey = config.getOpenaiApiKey();
        if (apiKey.isBlank()) {
            isDisabled = true;
        }

        openAiService = new OpenAiService(apiKey, TIMEOUT);

        ChatMessage setupMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(),
                """
                        Please answer questions in 1500 characters or less. Remember to count spaces in the
                        character limit. For code supplied for review, refer to the old code supplied rather than
                        rewriting the code. Don't supply a corrected version of the code.\s""");
        ChatCompletionRequest systemSetupRequest = ChatCompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .messages(List.of(setupMessage))
            .frequencyPenalty(0.5)
            .temperature(0.3)
            .maxTokens(50)
            .n(1)
            .build();

        // Sending the system setup message to ChatGPT.
        openAiService.createChatCompletion(systemSetupRequest);
    }

    /**
     * Prompt ChatGPT with a question and receive a response.
     * 
     * @param question The question being asked of ChatGPT. Max is {@value MAX_TOKENS} tokens.
     * @see <a href="https://platform.openai.com/docs/guides/chat/managing-tokens">ChatGPT
     *      Tokens</a>.
     * @return response from ChatGPT as a String.
     */
    public Optional<String[]> ask(String question) {
        if (isDisabled) {
            return Optional.empty();
        }

        try {
            ChatMessage chatMessage =
                    new ChatMessage(ChatMessageRole.USER.value(), Objects.requireNonNull(question));
            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(List.of(chatMessage))
                .frequencyPenalty(0.5)
                .temperature(0.3)
                .maxTokens(MAX_TOKENS)
                .n(1)
                .build();

            String response = openAiService.createChatCompletion(chatCompletionRequest)
                .getChoices()
                .get(0)
                .getMessage()
                .getContent();

            String[] aiResponses;
            if (response.length() > RESPONSE_LENGTH_LIMIT) {
                logger.warn(
                        "Response from AI was longer than allowed limit. "
                                + "The answer was cut up to max {} characters length messages",
                        RESPONSE_LENGTH_LIMIT);
                int begin = 0;
                int end = RESPONSE_LENGTH_LIMIT;
                int i = 0;
                aiResponses = new String[(response.length() / RESPONSE_LENGTH_LIMIT) + 1];
                while (begin < response.length()) {
                    aiResponses[i] = response.substring(begin, end);

                    begin += RESPONSE_LENGTH_LIMIT;
                    end += RESPONSE_LENGTH_LIMIT;
                    if (end > response.length()) {
                        end = response.length();
                    }
                    i++;
                }
            } else {
                aiResponses = new String[] {response};
            }

            return Optional.of(aiResponses);
        } catch (OpenAiHttpException openAiHttpException) {
            logger.warn(
                    "There was an error using the OpenAI API: {} Code: {} Type: {} Status Code: {}",
                    openAiHttpException.getMessage(), openAiHttpException.code,
                    openAiHttpException.type, openAiHttpException.statusCode);
        } catch (RuntimeException runtimeException) {
            logger.warn("There was an error using the OpenAI API: {}",
                    runtimeException.getMessage());
        }
        return Optional.empty();
    }
}
