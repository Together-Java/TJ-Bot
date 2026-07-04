package org.togetherjava.tjbot.features.chatgpt.tools;

import javax.annotation.Nullable;

/**
 * Outcome returned by a {@link AiTool#run(java.util.Map) tool invocation}. Either carries a
 * successfully produced payload or an error explaining why the call failed. The whole record is
 * serialized to JSON and fed back to the model as the tool's output.
 *
 * @param <T> payload type for successful invocations
 * @param result the payload on success; {@code null} when {@link #error()} flags an error
 * @param error error envelope; always non-null, but {@link AiToolToolError#isError()} indicates
 *        whether it represents an actual failure
 */
public record AiToolResult<T>(@Nullable T result, AiToolToolError error) {

    /**
     * Creates a successful result wrapping {@code value}.
     *
     * @param value payload produced by the tool
     * @param <T> payload type
     * @return a result carrying {@code value} with no error
     */
    public static <T> AiToolResult<T> ok(T value) {
        return new AiToolResult<>(value, AiToolToolError.none());
    }

    /**
     * Creates a failed result with the given message and no payload.
     *
     * @param message description of the failure, surfaced back to the model
     * @param <T> payload type the tool would otherwise have returned
     * @return a result with a {@code null} payload and an error envelope carrying {@code message}
     */
    public static <T> AiToolResult<T> failed(String message) {
        return new AiToolResult<>(null, AiToolToolError.of(message));
    }
}
