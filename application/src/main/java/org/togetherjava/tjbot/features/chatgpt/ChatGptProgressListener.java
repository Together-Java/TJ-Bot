package org.togetherjava.tjbot.features.chatgpt;

import org.togetherjava.tjbot.features.chatgpt.tools.AiTool;

import java.util.Map;

/**
 * Receives progress callbacks from
 * {@link ChatGptService#askWithTools(String, ChatGptModel, java.util.List, String, ChatGptProgressListener)}
 * so callers can surface intermediate state (e.g. update a Discord embed while the model is still
 * working).
 * <p>
 * All callbacks are invoked on the thread driving the call. Implementations must be quick and
 * thread-safe with respect to whatever UI they update.
 */
public interface ChatGptProgressListener {

    /** No-op instance, used when the caller does not care about progress events. */
    ChatGptProgressListener NO_OP = new ChatGptProgressListener() {};

    /**
     * Invoked at the start of each model turn, just before the OpenAI request is sent.
     *
     * @param round zero-based index of the current turn; {@code 0} is the first call to the model,
     *        higher values indicate follow-up turns after tool calls
     */
    default void onThinking(int round) {}

    /**
     * Invoked immediately before a tool is executed.
     *
     * @param toolName the {@link AiTool#name() name} of the tool the model has chosen to call
     * @param arguments parsed argument map the model produced; values are raw string form
     *        (objects/arrays appear as JSON text). Never {@code null}; may be empty.
     */
    default void onToolStart(String toolName, Map<String, String> arguments) {}

    /**
     * Invoked once a tool invocation has finished, whether it succeeded or failed.
     *
     * @param toolName the name of the tool that just completed
     * @param error {@code true} if the tool returned a failure result or threw; {@code false} on
     *        success
     * @param elapsedMs wall-clock time spent inside the tool's {@code run} method, in milliseconds
     */
    default void onToolEnd(String toolName, boolean error, long elapsedMs) {}
}
