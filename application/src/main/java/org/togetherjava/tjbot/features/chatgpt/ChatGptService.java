package org.togetherjava.tjbot.features.chatgpt;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.secrets.Secrets;

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

    /** The maximum number of tokens allowed for the generated answer. */
    private static final int MAX_TOKENS = 3_000;

    private boolean isDisabled = false;
    private OpenAIClient openAIClient;

    /**
     * Creates instance of ChatGPTService
     *
     * @param secrets needed for token to OpenAI API.
     */
    public ChatGptService(Secrets secrets) {
        String apiKey = secrets.getOpenaiApiKey();
        boolean keyIsDefaultDescription = apiKey.startsWith("<") && apiKey.endsWith(">");
        if (apiKey.isBlank() || keyIsDefaultDescription) {
            isDisabled = true;
            return;
        }
        openAIClient = OpenAIOkHttpClient.builder().apiKey(apiKey).timeout(TIMEOUT).build();
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
        if (isDisabled) {
            return Optional.empty();
        }

        String contextText = context == null ? "" : ", Context: %s.".formatted(context);
        String inputPrompt = """
                For code supplied for review, refer to the old code supplied rather than
                rewriting the code. DON'T supply a corrected version of the code.

                KEEP IT CONCISE, NOT MORE THAN 280 WORDS

                %s
                Question: %s
                """.formatted(contextText, question);

        logger.debug("ChatGpt request: {}", inputPrompt);

        String response = null;
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                .model(chatModel.toChatModel())
                .input(inputPrompt)
                .maxOutputTokens(MAX_TOKENS)
                .build();

            Response chatGptResponse = openAIClient.responses().create(params);

            response = chatGptResponse.output()
                .stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text)
                .collect(Collectors.joining("\n"));
        } catch (RuntimeException runtimeException) {
            logger.warn("There was an error using the OpenAI API: {}",
                    runtimeException.getMessage());
        }

        logger.debug("ChatGpt Response: {}", response);
        if (response == null) {
            return Optional.empty();
        }

        return Optional.of(response);
    }
}
