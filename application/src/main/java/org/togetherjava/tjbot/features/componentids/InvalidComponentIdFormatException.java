package org.togetherjava.tjbot.features.componentids;

import java.io.Serial;

/**
 * Exception that is thrown whenever a component ID is in an unexpected format and can not be
 * serialized or deserialized. See {@link ComponentIdGenerator} and {@link ComponentIdParser} for
 * details.
 */
public final class InvalidComponentIdFormatException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 2180184251986422000L;

    /**
     * Creates a new instance.
     */
    public InvalidComponentIdFormatException() {}

    /**
     * Creates a new instance with a given cause.
     *
     * @param cause the cause of this exception
     */
    public InvalidComponentIdFormatException(Throwable cause) {
        super(cause);
    }
}
