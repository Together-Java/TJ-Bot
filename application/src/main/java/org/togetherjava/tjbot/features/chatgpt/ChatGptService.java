package org.togetherjava.tjbot.features.chatgpt;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;

import javax.annotation.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service used to communicate to OpenAI API to generate responses.
 */
public class ChatGptService {
    private static final Logger logger = LoggerFactory.getLogger(ChatGptService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    /**
     * The maximum number of tokens allowed for the generated answer. This value is appropriate for
     * Discord's 2000 character message limit.
     */
    private static final int MAX_TOKENS = 500;

    private boolean isDisabled = false;
    private OpenAIClient openAIClient;

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
            logger.warn("ChatGPT service is disabled: API key is not configured");
            return;
        }
        openAIClient = OpenAIOkHttpClient.builder().apiKey(apiKey).timeout(TIMEOUT).build();
        logger.info("ChatGPT service initialized successfully");
    }

    /**
     * Prompt ChatGPT with a question and receive a response.
     *
     * @param question The question being asked of ChatGPT. Max is {@value MAX_TOKENS} tokens.
     * @param context The category of asked question, to set the context(eg. Java, Database, Other
     *        etc).
     * @param chatModel The AI model to use for this request.
     * @return response from ChatGPT as a String.
     * @see <a href="https://platform.openai.com/docs/guides/chat/managing-tokens">ChatGPT
     *      Tokens</a>.
     */
    public Optional<String> ask(String question, @Nullable String context, ChatGptModel chatModel) {
        String contextText = context == null ? "" : ", Context: %s.".formatted(context);
        String inputPrompt = """
                For code supplied for review, refer to the old code supplied rather than
                rewriting the code. DON'T supply a corrected version of the code.

                KEEP IT CONCISE, NOT MORE THAN 280 WORDS

                %s
                Question: %s
                """.formatted(contextText, question);

        return sendPrompt(inputPrompt, chatModel);
    }

    /**
     * Prompt ChatGPT with a raw prompt and receive a response without any prefix wrapping.
     * <p>
     * Use this method when you need full control over the prompt structure without the service's
     * opinionated formatting (e.g., for iterative refinement or specialized use cases).
     *
     * @param inputPrompt The raw prompt to send to ChatGPT. Max is {@value MAX_TOKENS} tokens.
     * @param chatModel The AI model to use for this request.
     * @return response from ChatGPT as a String.
     * @see <a href="https://platform.openai.com/docs/guides/chat/managing-tokens">ChatGPT
     *      Tokens</a>.
     */
    public Optional<String> askRaw(String inputPrompt, ChatGptModel chatModel) {
        return sendPrompt(inputPrompt, chatModel);
    }

    /**
     * Sends a prompt to the ChatGPT API and returns the response.
     *
     * @param prompt The prompt to send to ChatGPT.
     * @param chatModel The AI model to use for this request.
     * @return response from ChatGPT as a String.
     */
    private Optional<String> sendPrompt(String prompt, ChatGptModel chatModel) {
        if (isDisabled) {
            logger.warn("ChatGPT request attempted but service is disabled");
            return Optional.empty();
        }

        logger.debug("ChatGpt request: {}", prompt);

        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                .model(chatModel.toChatModel())
                .input(prompt)
                .maxOutputTokens(MAX_TOKENS)
                .build();

            Response chatGptResponse = openAIClient.responses().create(params);

            String response = chatGptResponse.output()
                .stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text)
                .collect(Collectors.joining("\n"));

            logger.debug("ChatGpt Response: {}", response);

            if (response.isBlank()) {
                logger.warn("ChatGPT returned an empty response");
                return Optional.empty();
            }

            logger.debug("ChatGpt response received successfully, length: {} characters",
                    response.length());
            return Optional.of(response);
        } catch (RuntimeException runtimeException) {
            logger.error("Error communicating with OpenAI API: {}", runtimeException.getMessage(),
                    runtimeException);
            return Optional.empty();
        }
    }
}
