package org.togetherjava.tjbot.db;


import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.togetherjava.tjbot.util.CheckedConsumer;
import org.togetherjava.tjbot.util.CheckedFunction;

import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The main database class.
 */
public final class Database {

    private final DSLContext dslContext;
    private final Lock writeLock = new ReentrantLock();

    /**
     * Creates a new database.
     *
     * @param jdbcUrl the url to the database
     * @throws SQLException if no connection could be established
     */
    public Database(String jdbcUrl) throws SQLException {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enforceForeignKeys(true);
        // In WAL mode only concurrent writes pose a problem so we synchronize on those
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);

        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl(jdbcUrl);

        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("/db").load();
        flyway.migrate();

        dslContext = DSL.using(dataSource.getConnection(), SQLDialect.SQLITE);
    }

    /**
     * Acquires read access to the database.
     *
     * @return a way to interact with the database in a read only way
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public <T> T read(
            CheckedFunction<? super DSLContext, T, ? extends DataAccessException> action) {
        try {
            return action.accept(getDslContext());
        } catch (DataAccessException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Acquires read access to the database.
     *
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public void read(CheckedConsumer<? super DSLContext, ? extends DataAccessException> action) {
        read(context -> {
            action.accept(context);
            return null;
        });
    }

    /**
     * Acquires read and write access to the database.
     *
     * @return a way to interact with the database
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public <T> T write(
            CheckedFunction<? super DSLContext, T, ? extends DataAccessException> action) {
        writeLock.lock();
        try {
            return action.accept(getDslContext());
        } catch (DataAccessException e) {
            throw new DatabaseException(e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Acquires read and write access to the database.
     *
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public void write(CheckedConsumer<? super DSLContext, ? extends DataAccessException> action) {
        write(context -> {
            action.accept(context);
            return null;
        });
    }

    /**
     * Acquires a transaction that can only read from the database.
     *
     * @param handler the handler that is executed within the context of the transaction. The
     *        handler will be called once and its return value returned from the transaction.
     * @param <T> the handler's return type
     * @return whatever the handler returned#
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public <T> T readTransaction(
            CheckedFunction<? super DSLContext, T, DataAccessException> handler) {
        var holder = new ResultHolder<T>();

        try {
            getDslContext().transaction(config -> holder.result = handler.accept(config.dsl()));
        } catch (DataAccessException e) {
            throw new DatabaseException(e);
        }

        return holder.result;
    }

    /**
     * Acquires a transaction that can only read from the database.
     *
     * @param handler the handler that is executed within the context of the transaction. It has no
     *        return value.
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public void readTransaction(
            CheckedConsumer<? super DSLContext, ? extends DataAccessException> handler) {
        readTransaction(dsl -> {
            handler.accept(dsl);
            return null;
        });
    }

    /**
     * Acquires a transaction that can read and write to the database.
     *
     * @param handler the handler that is executed within the context of the transaction. The
     *        handler will be called once and its return value returned from the transaction.
     * @param <T> the handler's return type
     * @return whatever the handler returned
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public <T> T writeTransaction(
            CheckedFunction<? super DSLContext, T, DataAccessException> handler) {
        var holder = new ResultHolder<T>();

        writeLock.lock();
        try {
            getDslContext().transaction(config -> holder.result = handler.accept(config.dsl()));
        } catch (DataAccessException e) {
            throw new DatabaseException(e);
        } finally {
            writeLock.unlock();
        }

        return holder.result;
    }

    /**
     * Acquires a transaction that can read and write to the database.
     *
     * @param handler the handler that is executed within the context of the transaction. It has no
     *        return value.
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public void writeTransaction(
            CheckedConsumer<? super DSLContext, ? extends DataAccessException> handler) {
        writeTransaction(dsl -> {
            handler.accept(dsl);
            return null;
        });
    }

    /**
     * @return the database dsl context
     */
    private DSLContext getDslContext() {
        return dslContext;
    }

    /**
     * Utility classed used to wrap a result, for example to bypass <i>effectively final</i>
     * restrictions.
     *
     * @param <T> the type of the result to hold
     */
    private static class ResultHolder<T> {
        private T result;
    }
}

