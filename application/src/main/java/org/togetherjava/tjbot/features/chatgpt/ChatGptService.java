package org.togetherjava.tjbot.features.chatgpt;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.Tool;
import com.openai.models.vectorstores.VectorStore;
import com.openai.models.vectorstores.VectorStoreCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.analytics.Metrics;

import javax.annotation.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service used to communicate to OpenAI API to generate responses.
 */
public class ChatGptService {
    private static final Logger logger = LoggerFactory.getLogger(ChatGptService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    /** The maximum number of tokens allowed for the generated answer. */
    private static final int MAX_TOKENS = 1000;

    private boolean isDisabled = false;
    private OpenAIClient openAIClient;
    private Metrics metrics;

    /**
     * Creates instance of ChatGPTService
     *
     * @param config needed for token to OpenAI API.
     * @param metrics to track events
     */
    public ChatGptService(Config config, Metrics metrics) {
        String apiKey = config.getOpenaiApiKey();
        this.metrics = metrics;

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
    public Optional<String> sendPrompt(String prompt, ChatGptModel chatModel) {
        return sendPrompt(prompt, chatModel, List.of());
    }

    /**
     * Lists all files uploaded to OpenAI and returns the ID of the first file matching the given
     * filename (case-insensitive).
     *
     * @param filePath The filename to search for among uploaded files.
     * @return An Optional containing the file ID if found, or empty if no matching file exists.
     */
    public Optional<String> getUploadedFileId(String filePath) {
        return openAIClient.files()
            .list()
            .items()
            .stream()
            .filter(fileObj -> fileObj.filename().equalsIgnoreCase(filePath))
            .map(FileObject::id)
            .findFirst();
    }

    /**
     * Uploads the specified file to OpenAI if it exists locally and hasn't been uploaded before.
     *
     * @param filePath The local path to the file to upload.
     * @param purpose The OpenAI file purpose (e.g., {@link FilePurpose#ASSISTANTS})
     * @return an Optional containing the uploaded file ID, or empty if:
     *         <ul>
     *         <li>service is disabled</li>
     *         <li>file doesn't exist locally</li>
     *         <li>file with matching name already uploaded</li>
     *         </ul>
     */
    public Optional<String> uploadFileIfNotExists(Path filePath, FilePurpose purpose) {
        if (isDisabled) {
            logger.warn("ChatGPT file upload attempted but service is disabled");
            return Optional.empty();
        }

        if (!Files.notExists(filePath)) {
            logger.warn("Could not find file '{}' to upload to ChatGPT", filePath);
            return Optional.empty();
        }

        if (getUploadedFileId(filePath.toString()).isPresent()) {
            logger.warn("File '{}' already exists.", filePath);
            return Optional.empty();
        }

        FileCreateParams fileCreateParams =
                FileCreateParams.builder().file(filePath).purpose(purpose).build();

        FileObject fileObj = openAIClient.files().create(fileCreateParams);
        String id = fileObj.id();

        logger.info("Uploaded file to ChatGPT with ID {}", id);
        return Optional.of(id);
    }

    /**
     * Creates a new vector store with the given file ID if none exists or returns the ID of the
     * existing vector store with that name.
     * <p>
     * A vector store indexes document content as embeddings for semantic search. You can use this
     * for RAG (Retrieval-Augmented Generation), where the model retrieves relevant context from
     * your documents before generating responses, effectively giving it access to information
     * beyond its training data.
     *
     * @param fileId The ID of the file to include in the new vector store.
     * @return The vector store ID (existing or newly created).
     */
    public String createOrGetVectorStore(String fileId, String vectorStoreName) {
        List<VectorStore> vectorStores = openAIClient.vectorStores()
            .list()
            .items()
            .stream()
            .filter(vectorStore -> vectorStore.name().equalsIgnoreCase(vectorStoreName))
            .toList();
        Optional<VectorStore> vectorStore = vectorStores.stream().findFirst();

        if (vectorStore.isPresent()) {
            String vectorStoreId = vectorStore.get().id();
            logger.debug("Got vector store {}", vectorStoreId);
            return vectorStoreId;
        }

        VectorStoreCreateParams params = VectorStoreCreateParams.builder()
            .name(vectorStoreName)
            .fileIds(List.of(fileId))
            .build();

        VectorStore newVectorStore = openAIClient.vectorStores().create(params);
        String vectorStoreId = newVectorStore.id();

        logger.debug("Created vector store {}", vectorStoreId);
        return vectorStoreId;
    }

    /**
     * Sends a prompt to the ChatGPT API and returns the response.
     *
     * @param prompt The prompt to send to ChatGPT.
     * @param chatModel The AI model to use for this request.
     * @param tools The list of OpenAPI tools to enhance the prompt's answers.
     * @return response from ChatGPT as a String.
     */
    public Optional<String> sendPrompt(String prompt, ChatGptModel chatModel, List<Tool> tools) {
        if (isDisabled) {
            logger.warn("ChatGPT request attempted but service is disabled");
            return Optional.empty();
        }

        logger.debug("ChatGpt request: {}", prompt);

        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                .model(chatModel.toChatModel())
                .input(prompt)
                .tools(tools)
                .maxOutputTokens(MAX_TOKENS)
                .build();

            Response chatGptResponse = openAIClient.responses().create(params);
            metrics.count("chatgpt-prompted");

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
