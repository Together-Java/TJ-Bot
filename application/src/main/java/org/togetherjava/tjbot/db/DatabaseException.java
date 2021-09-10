package org.togetherjava.tjbot.db;

/**
 * Thrown when an error occurs while interacting with the database.
 */
public class DatabaseException extends RuntimeException {
    public DatabaseException(Throwable cause) {
        super(cause);
    }
}
