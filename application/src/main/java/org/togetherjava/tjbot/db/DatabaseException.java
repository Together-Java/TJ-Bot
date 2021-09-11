package org.togetherjava.tjbot.db;

import java.io.Serial;

/**
 * Thrown when an error occurs while interacting with the database.
 */
public class DatabaseException extends RuntimeException {
    /**
     * Serial version UID.
     */
    @Serial
    private static final long serialVersionUID = -4215197259643585552L;
    
    public DatabaseException(Exception cause) {
        super(cause);
    }
}
