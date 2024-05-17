package org.togetherjava.tjbot.features.chatgpt;

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
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    /** The maximum number of tokens allowed for the generated answer. */
    private static final int MAX_TOKENS = 3_000;

    /**
     * This parameter reduces the likelihood of the AI repeating itself. A higher frequency penalty
     * makes the model less likely to repeat the same lines verbatim. It helps in generating more
     * diverse and varied responses.
     */
    private static final double FREQUENCY_PENALTY = 0.5;

    /**
     * This parameter controls the randomness of the AI's responses. A higher temperature results in
     * more varied, unpredictable, and creative responses. Conversely, a lower temperature makes the
     * model's responses more deterministic and conservative.
     */
    private static final double TEMPERATURE = 0.8;

    /**
     * n: This parameter specifies the number of responses to generate for each prompt. If n is more
     * than 1, the AI will generate multiple different responses to the same prompt, each one being
     * a separate iteration based on the input.
     */
    private static final int MAX_NUMBER_OF_RESPONSES = 1;
    private static final String AI_MODEL = "gpt-3.5-turbo";

    private boolean isDisabled = false;
    private OpenAiService openAiService;

    /**
     * Creates instance of ChatGPTService
     *
     * @param config needed for token to OpenAI API.
     */
    public ChatGptService(Config config) {
        String apiKey = config.getOpenaiApiKey();
        boolean keyIsDefaultDescription = apiKey.startsWith("<") && apiKey.endsWith(">");
        if (apiKey.isBlank() || keyIsDefaultDescription) {
            isDisabled = true;
            return;
        }

        openAiService = new OpenAiService(apiKey, TIMEOUT);

        ChatMessage setupMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), """
                For code supplied for review, refer to the old code supplied rather than
                rewriting the code. DON'T supply a corrected version of the code.\s""");
        ChatCompletionRequest systemSetupRequest = ChatCompletionRequest.builder()
            .model(AI_MODEL)
            .messages(List.of(setupMessage))
            .frequencyPenalty(FREQUENCY_PENALTY)
            .temperature(TEMPERATURE)
            .maxTokens(50)
            .n(MAX_NUMBER_OF_RESPONSES)
            .build();

        // Sending the system setup message to ChatGPT.
        openAiService.createChatCompletion(systemSetupRequest);
    }

    /**
     * Prompt ChatGPT with a question and receive a response.
     *
     * @param question The question being asked of ChatGPT. Max is {@value MAX_TOKENS} tokens.
     * @param context The category of asked question, to set the context(eg. Java, Database, Other
     *        etc).
     * @return response from ChatGPT as a String.
     * @see <a href="https://platform.openai.com/docs/guides/chat/managing-tokens">ChatGPT
     *      Tokens</a>.
     */
    public Optional<String> ask(String question, String context) {
        if (isDisabled) {
            return Optional.empty();
        }

        try {
            String instructions = "KEEP IT CONCISE, NOT MORE THAN 280 WORDS";
            String questionWithContext = "context: Category %s on a Java Q&A discord server. %s %s"
                .formatted(context, instructions, question);
            ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(),
                    Objects.requireNonNull(questionWithContext));
            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(AI_MODEL)
                .messages(List.of(chatMessage))
                .frequencyPenalty(FREQUENCY_PENALTY)
                .temperature(TEMPERATURE)
                .maxTokens(MAX_TOKENS)
                .n(MAX_NUMBER_OF_RESPONSES)
                .build();

            String response = openAiService.createChatCompletion(chatCompletionRequest)
                .getChoices()
                .getFirst()
                .getMessage()
                .getContent();

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(response);
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
