package org.togetherjava.tjbot.commands.componentids;

import java.io.Serial;

public final class InvalidComponentIdFormatException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 2180184251986422000L;

    public InvalidComponentIdFormatException() {}

    public InvalidComponentIdFormatException(Throwable cause) {
        super(cause);
    }
}
