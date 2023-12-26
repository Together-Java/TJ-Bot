package org.togetherjava.tjbot.db;

import java.io.Serial;

/**
 * Thrown when an error occurs while interacting with the database.
 */
public final class DatabaseException extends RuntimeException {
    /**
     * Serial version UID.
     */
    @Serial
    private static final long serialVersionUID = -4215197259643585552L;

    /**
     * Creates a new instance of this exception with the given underlying cause.
     *
     * @param cause The cause of the exception
     */
    public DatabaseException(Exception cause) {
        super(cause);
    }
}
