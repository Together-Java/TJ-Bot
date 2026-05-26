package org.togetherjava.tjbot.features.chatgpt.tools;

import java.util.List;
import java.util.Map;

/**
 * A function-style tool that ChatGPT can invoke during a {@code askWithTools} call.
 * <p>
 * Implementations describe themselves to the model via {@link #name()}, {@link #description()} and
 * {@link #parameters()}, and execute the call in {@link #run(Map)}. The {@code Map<String, String>}
 * arguments mirror the JSON object the model returns: each argument is the raw string form
 * (objects/arrays are passed through as their JSON text).
 *
 * @param <T> result payload type, serialized to JSON when returned to the model
 */
public interface AiTool<T> {

    /**
     * Stable identifier used by the model to invoke this tool. Must be unique within the tool list
     * passed to a single {@code askWithTools} call.
     *
     * @return the tool's name (e.g. {@code "web_search"})
     */
    String name();

    /**
     * Natural-language description shown to the model so it can decide when to call this tool.
     * Should focus on <em>when</em> to use it, not the implementation details.
     *
     * @return human-readable description
     */
    String description();

    /**
     * Declares the arguments the model is allowed to supply. Translated into a JSON schema attached
     * to the tool definition sent to OpenAI.
     *
     * @return the list of parameters; may be empty if the tool takes no arguments
     */
    List<AiToolParameter> parameters();

    /**
     * Executes the tool with the arguments the model produced.
     *
     * @param arguments argument map keyed by {@link AiToolParameter#name()}; values are the raw
     *        string form (objects/arrays appear as their JSON text). Missing optional arguments are
     *        absent from the map rather than mapped to {@code null}.
     * @return the outcome — either a successful payload or a failure envelope; never {@code null}
     */
    AiToolResult<T> run(Map<String, String> arguments);
}
