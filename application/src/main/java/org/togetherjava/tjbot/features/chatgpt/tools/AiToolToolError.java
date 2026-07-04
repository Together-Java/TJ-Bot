package org.togetherjava.tjbot.features.chatgpt.tools;

import javax.annotation.Nullable;

/**
 * Error envelope attached to a {@link AiToolResult}. Serialized as JSON and returned to the model
 * so it can recover (retry, try a different tool, or answer without the missing data) rather than
 * the whole request blowing up.
 *
 * @param isError {@code true} if the tool failed; {@code false} for a successful invocation
 * @param errorMessage human-readable failure reason when {@code isError} is true, otherwise
 *        {@code null}
 */
public record AiToolToolError(boolean isError, @Nullable String errorMessage) {

    /**
     * Returns a sentinel "no error" envelope.
     *
     * @return an envelope with {@link #isError()} {@code false} and a {@code null} message
     */
    public static AiToolToolError none() {
        return new AiToolToolError(false, null);
    }

    /**
     * Wraps a failure message into an error envelope.
     *
     * @param message description of what went wrong; surfaced back to the model
     * @return an error-flagged envelope carrying {@code message}
     */
    public static AiToolToolError of(String message) {
        return new AiToolToolError(true, message);
    }
}
