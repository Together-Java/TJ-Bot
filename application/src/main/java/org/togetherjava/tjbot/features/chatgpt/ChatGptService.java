package org.togetherjava.tjbot.features.chatgpt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.analytics.Metrics;
import org.togetherjava.tjbot.features.chatgpt.schema.ResponseSchema;
import org.togetherjava.tjbot.features.chatgpt.tools.AiTool;
import org.togetherjava.tjbot.features.chatgpt.tools.AiToolParameter;
import org.togetherjava.tjbot.features.chatgpt.tools.AiToolResult;

import javax.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service used to communicate to OpenAI API to generate responses.
 */
public class ChatGptService {
    private static final Logger logger = LoggerFactory.getLogger(ChatGptService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_SCHEMA_NAME = "response";

    /** The maximum number of tokens allowed for the generated answer. */
    private static final int MAX_TOKENS = 1000;

    /** Safety cap on tool-call rounds for a single {@code askWithTools} invocation. */
    private static final int MAX_TOOL_ROUNDS = 8;

    /**
     * Appended to the system prompt for the forced-finish call after the tool-round cap is hit, so
     * the model produces text instead of asking for another tool.
     */
    private static final String FORCE_FINAL_ANSWER_INSTRUCTION = """
            You have reached the maximum number of tool-call rounds. Do NOT call any more tools.
            Answer the user's question now using only what you have already gathered. If you do
            not have enough information for a complete answer, give your best partial response
            and say plainly what you could not determine.""";

    private boolean isDisabled = false;
    private OpenAIClient openAIClient;
    private final Metrics metrics;

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
     * @param context The category of asked question, to set the context(e.g. Java, Database, Other
     *        etc.).
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

        return sendPrompt(inputPrompt, chatModel, null);
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
        return sendPrompt(inputPrompt, chatModel, null);
    }

    /**
     * Prompt ChatGPT with a raw prompt and receive a JSON response conforming to the given schema.
     * <p>
     * Uses OpenAI's structured outputs feature so the model is constrained to return JSON matching
     * the supplied schema.
     *
     * @param inputPrompt The raw prompt to send to ChatGPT. Max is {@value MAX_TOKENS} tokens.
     * @param chatModel The AI model to use for this request.
     * @param schema The JSON schema the response must conform to.
     * @return response from ChatGPT as a JSON string conforming to {@code schema}.
     */
    public Optional<String> askRaw(String inputPrompt, ChatGptModel chatModel,
            ResponseSchema schema) {
        return sendPrompt(inputPrompt, chatModel, schema);
    }

    /**
     * Sends a prompt to the ChatGPT API and returns the response.
     *
     * @param prompt The prompt to send to ChatGPT.
     * @param chatModel The AI model to use for this request.
     * @param schema Optional JSON schema constraining the model output; {@code null} for free-form.
     * @return response from ChatGPT as a String.
     */
    private Optional<String> sendPrompt(String prompt, ChatGptModel chatModel,
            @Nullable ResponseSchema schema) {
        if (isDisabled) {
            logger.warn("ChatGPT request attempted but service is disabled");
            return Optional.empty();
        }

        logger.debug("ChatGpt request: {}", prompt);

        try {
            ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams.builder()
                .model(chatModel.toChatModel())
                .input(prompt)
                .maxOutputTokens(MAX_TOKENS);

            if (schema != null) {
                paramsBuilder.text(buildTextConfig(schema));
            }

            ResponseCreateParams params = paramsBuilder.build();

            Response chatGptResponse = openAIClient.responses().create(params);
            logMetric();

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

    /**
     * Prompt ChatGPT with a question and a set of tools the model is allowed to call.
     * <p>
     * The service loops: each turn it sends the conversation, executes any tool calls the model
     * requests, appends their results, and continues until the model returns a final text answer
     * (or {@link #MAX_TOOL_ROUNDS} is reached).
     *
     * @param userPrompt the user-facing question
     * @param chatModel the AI model to use
     * @param tools tools the model may invoke; pass an empty collection to disable tool use
     * @param systemPrompt optional system instructions framing the assistant's behaviour
     * @return the model's final text response, or empty on error / no answer
     */
    public Optional<String> askWithTools(String userPrompt, ChatGptModel chatModel,
            List<? extends AiTool<?>> tools, @Nullable String systemPrompt) {
        return askWithTools(userPrompt, chatModel, tools, systemPrompt,
                ChatGptProgressListener.NO_OP);
    }

    /**
     * Variant of {@link #askWithTools(String, ChatGptModel, List, String)} that reports progress
     * (per-turn thinking and tool invocations) through {@code listener} so callers can surface
     * intermediate state to the user.
     *
     * @param userPrompt the user-facing question
     * @param chatModel the AI model to use
     * @param tools tools the model may invoke; pass an empty collection to disable tool use
     * @param systemPrompt optional system instructions framing the assistant's behaviour;
     *        {@code null} or blank skips the instructions field
     * @param listener receives per-turn and per-tool-call callbacks; use
     *        {@link ChatGptProgressListener#NO_OP} when progress is not needed
     * @return the model's final text response, or empty on error, blank response, or if the
     *         {@link #MAX_TOOL_ROUNDS tool-round cap} is exceeded without a final answer
     */
    public Optional<String> askWithTools(String userPrompt, ChatGptModel chatModel,
            List<? extends AiTool<?>> tools, @Nullable String systemPrompt,
            ChatGptProgressListener listener) {
        if (isDisabled) {
            logger.warn("ChatGPT request attempted but service is disabled");
            return Optional.empty();
        }

        Map<String, AiTool<?>> toolsByName = new LinkedHashMap<>();
        for (AiTool<?> tool : tools) {
            toolsByName.put(tool.name(), tool);
        }

        List<ResponseInputItem> conversation = new ArrayList<>();
        conversation.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
            .role(EasyInputMessage.Role.USER)
            .content(userPrompt)
            .build()));

        List<Tool> openAiTools = toOpenAiTools(toolsByName.values());

        try {
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                listener.onThinking(round);
                Response response = openAIClient.responses()
                    .create(buildRequestParams(chatModel, conversation, systemPrompt, openAiTools));
                logMetric();

                List<ResponseFunctionToolCall> toolCalls = response.output()
                    .stream()
                    .flatMap(item -> item.functionCall().stream())
                    .toList();

                if (toolCalls.isEmpty()) {
                    return finalAnswerFrom(response);
                }
                appendToolCallsToConversation(conversation, response, toolCalls, toolsByName,
                        listener);
            }
            logger.warn(
                    "ChatGPT exceeded {} tool rounds; forcing a final answer with tools disabled",
                    MAX_TOOL_ROUNDS);
            return forceFinalAnswer(chatModel, conversation, systemPrompt);
        } catch (RuntimeException e) {
            logger.error("Error during tool-enabled ChatGPT call: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static ResponseCreateParams buildRequestParams(ChatGptModel chatModel,
            List<ResponseInputItem> conversation, @Nullable String systemPrompt,
            List<Tool> openAiTools) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
            .model(chatModel.toChatModel())
            .inputOfResponse(List.copyOf(conversation))
            .maxOutputTokens(MAX_TOKENS);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.instructions(systemPrompt);
        }
        if (!openAiTools.isEmpty()) {
            builder.tools(openAiTools).toolChoice(ToolChoiceOptions.AUTO);
        }
        return builder.build();
    }

    private Optional<String> forceFinalAnswer(ChatGptModel chatModel,
            List<ResponseInputItem> conversation, @Nullable String systemPrompt) {
        String forcedInstructions =
                systemPrompt == null || systemPrompt.isBlank() ? FORCE_FINAL_ANSWER_INSTRUCTION
                        : systemPrompt + "\n\n" + FORCE_FINAL_ANSWER_INSTRUCTION;
        try {
            Response response = openAIClient.responses()
                .create(buildRequestParams(chatModel, conversation, forcedInstructions, List.of()));
            logMetric();
            return finalAnswerFrom(response);
        } catch (RuntimeException e) {
            logger.error("Forced final-answer call failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static Optional<String> finalAnswerFrom(Response response) {
        String text = extractText(response);
        if (text.isBlank()) {
            logger.warn("ChatGPT returned an empty response (tools-enabled)");
            return Optional.empty();
        }
        return Optional.of(text);
    }

    private void appendToolCallsToConversation(List<ResponseInputItem> conversation,
            Response response, List<ResponseFunctionToolCall> toolCalls,
            Map<String, AiTool<?>> toolsByName, ChatGptProgressListener listener) {
        for (ResponseOutputItem item : response.output()) {
            toInputItem(item).ifPresent(conversation::add);
        }
        for (ResponseFunctionToolCall call : toolCalls) {
            String output = executeTool(toolsByName, call, listener);
            conversation.add(ResponseInputItem
                .ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                    .callId(call.callId())
                    .output(output)
                    .build()));
        }
    }

    private String executeTool(Map<String, AiTool<?>> toolsByName, ResponseFunctionToolCall call,
            ChatGptProgressListener listener) {
        AiTool<?> tool = toolsByName.get(call.name());
        if (tool == null) {
            if (logger.isInfoEnabled()) {
                logger.info("ChatGPT requested unknown tool '{}' with args {}", call.name(),
                        call.arguments());
            }
            return toolErrorJson("Unknown tool: " + call.name());
        }
        Map<String, String> arguments = parseArguments(call.arguments());
        if (logger.isInfoEnabled()) {
            logger.info("ChatGPT invoking tool '{}' with args {}", call.name(), arguments);
        }
        listener.onToolStart(call.name(), arguments);
        long startNanos = System.nanoTime();
        try {
            AiToolResult<?> result = tool.run(arguments);
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            boolean errored = result.error().isError();
            if (logger.isInfoEnabled()) {
                if (errored) {
                    logger.info("Tool '{}' returned error after {} ms: {}", call.name(), elapsedMs,
                            result.error().errorMessage());
                } else {
                    logger.info("Tool '{}' completed in {} ms", call.name(), elapsedMs);
                }
            }
            listener.onToolEnd(call.name(), errored, elapsedMs);
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            logger.warn("Tool {} produced unserializable result", call.name(), e);
            listener.onToolEnd(call.name(), true, (System.nanoTime() - startNanos) / 1_000_000L);
            return toolErrorJson("Failed to serialize result of " + call.name());
        } catch (RuntimeException e) {
            logger.error("Tool {} threw", call.name(), e);
            listener.onToolEnd(call.name(), true, (System.nanoTime() - startNanos) / 1_000_000L);
            return toolErrorJson("Tool " + call.name() + " failed: " + e.getMessage());
        }
    }

    private static Map<String, String> parseArguments(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!root.isObject()) {
                return Map.of();
            }
            Map<String, String> args = new LinkedHashMap<>();
            root.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    args.put(entry.getKey(), "");
                } else if (value.isValueNode()) {
                    args.put(entry.getKey(), value.asText());
                } else {
                    args.put(entry.getKey(), value.toString());
                }
            });
            return args;
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse tool arguments JSON: {}", json, e);
            return Map.of();
        }
    }

    private static String toolErrorJson(String message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException _) {
            return "{\"error\":\"tool failure\"}";
        }
    }

    private static Optional<ResponseInputItem> toInputItem(ResponseOutputItem item) {
        return item.functionCall()
            .map(ResponseInputItem::ofFunctionCall)
            .or(() -> item.reasoning().map(ResponseInputItem::ofReasoning))
            .or(() -> item.message().map(ResponseInputItem::ofResponseOutputMessage));
    }

    private static String extractText(Response response) {
        return response.output()
            .stream()
            .flatMap(item -> item.message().stream())
            .flatMap(message -> message.content().stream())
            .flatMap(content -> content.outputText().stream())
            .map(ResponseOutputText::text)
            .collect(Collectors.joining("\n"));
    }

    private static List<Tool> toOpenAiTools(Collection<? extends AiTool<?>> tools) {
        List<Tool> result = new ArrayList<>(tools.size());
        for (AiTool<?> tool : tools) {
            result.add(Tool.ofFunction(toFunctionTool(tool)));
        }
        return result;
    }

    private static FunctionTool toFunctionTool(AiTool<?> tool) {
        FunctionTool.Parameters.Builder schemaBuilder = FunctionTool.Parameters.builder();
        Map<String, Object> schema = buildParametersSchema(tool.parameters());
        schema.forEach(
                (key, value) -> schemaBuilder.putAdditionalProperty(key, JsonValue.from(value)));

        return FunctionTool.builder()
            .name(tool.name())
            .description(tool.description())
            .parameters(schemaBuilder.build())
            .strict(false)
            .build();
    }

    private static Map<String, Object> buildParametersSchema(
            List<AiToolParameter> aiToolParameters) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (AiToolParameter aiToolParameter : aiToolParameters) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", aiToolParameter.type().toJsonSchemaType());
            property.put("description", aiToolParameter.description());
            properties.put(aiToolParameter.name(), property);
            if (aiToolParameter.required()) {
                required.add(aiToolParameter.name());
            }
        }
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ResponseTextConfig buildTextConfig(ResponseSchema schema) {
        Map<String, Object> schemaMap =
                OBJECT_MAPPER.convertValue(schema, new TypeReference<>() {});

        ResponseFormatTextJsonSchemaConfig.Schema.Builder schemaBuilder =
                ResponseFormatTextJsonSchemaConfig.Schema.builder();
        schemaMap.forEach(
                (key, value) -> schemaBuilder.putAdditionalProperty(key, JsonValue.from(value)));

        ResponseFormatTextJsonSchemaConfig jsonSchemaConfig =
                ResponseFormatTextJsonSchemaConfig.builder()
                    .name(DEFAULT_SCHEMA_NAME)
                    .strict(true)
                    .schema(schemaBuilder.build())
                    .build();

        return ResponseTextConfig.builder().format(jsonSchemaConfig).build();
    }

    private void logMetric() {
        metrics.count("chatgpt-prompted");
    }
}
