package org.togetherjava.tjbot.db;

import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import org.togetherjava.tjbot.db.util.CheckedConsumer;
import org.togetherjava.tjbot.db.util.CheckedFunction;

import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The main database class used by the application.
 * <p>
 * Create an instance using {@link #Database(String)} and prefer to re-use it. The underlying
 * connections are handled automatically by the system.
 * <p>
 * Instances of this class are thread-safe and can be used to concurrently write to the database.
 */
public final class Database {

    static {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");
    }

    private final DSLContext dslContext;
    /**
     * Lock used to implement thread-safety across this class. Any database modifying method must
     * use this lock.
     */
    private final Lock writeLock = new ReentrantLock();

    /**
     * Creates an instance of a new database.
     *
     * @param jdbcUrl the url to the database in the format expected by JDBC
     * @throws SQLException if no connection could be established
     */
    public Database(String jdbcUrl) throws SQLException {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enforceForeignKeys(true);
        // In WAL mode only concurrent writes pose a problem, so we synchronize those
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);

        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl(jdbcUrl);

        Flyway flyway =
                Flyway.configure().dataSource(dataSource).locations("classpath:/db/").load();
        flyway.migrate();

        dslContext = DSL.using(dataSource.getConnection(), SQLDialect.SQLITE);
    }

    /**
     * Creates a new empty database that is hold in memory.
     *
     * @param tables the tables the database will hold if desired, otherwise null
     * @return the created database
     */
    public static Database createMemoryDatabase(Table<?>... tables) {
        try {
            Database database = new Database("jdbc:sqlite:");
            database.write(context -> context.ddl(tables).executeBatch());
            return database;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Acquires read-only access to the database.
     *
     * @param action the action to apply to the DSL context, e.g. a query
     * @param <T> the type returned by the given action
     * @return the result returned by the given action
     * @throws DatabaseException if an error occurs in the given action
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
     * Acquires read-only access to the database and consumes the result directly.
     *
     * @param action the action that consumes the DSL context, e.g. a query
     * @throws DatabaseException if an error occurs in the given action
     */
    public void readAndConsume(
            CheckedConsumer<? super DSLContext, ? extends DataAccessException> action) {
        read(context -> {
            action.accept(context);
            // noinspection ReturnOfNull
            return null;
        });
    }

    /**
     * Acquires read and write access to the database and provides the computed result.
     *
     * @param action the action to apply to the DSL context, e.g. a query
     * @param <T> the type returned by the given action
     * @return the result returned by the given action
     * @throws DatabaseException if an error occurs in the given action
     */
    public <T> T writeAndProvide(
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
     * @param action the action to apply to the DSL context, e.g. a query
     * @throws DatabaseException if an error occurs in the given action
     */
    public void write(CheckedConsumer<? super DSLContext, ? extends DataAccessException> action) {
        writeAndProvide(context -> {
            action.accept(context);
            // noinspection ReturnOfNull
            return null;
        });
    }

    /**
     * Acquires a transaction that can only read from the database.
     *
     * @param handler the handler that is executed within the context of the transaction. The
     *        handler will be called once and its return value returned from the transaction.
     * @param <T> the handler's return type
     * @return the object that is returned by the given handler
     * @throws DatabaseException if an error occurs in the given handler function
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
     * Acquires a transaction that can only read from the database and consumes the result directly.
     *
     * @param handler the handler that is executed within the context of the transaction. It has no
     *        return value.
     * @throws DatabaseException if an error occurs in the given handler function
     */
    public void readTransactionAndConsume(
            CheckedConsumer<? super DSLContext, ? extends DataAccessException> handler) {
        readTransaction(dsl -> {
            handler.accept(dsl);
            // noinspection ReturnOfNull
            return null;
        });
    }

    /**
     * Acquires a transaction that can read and write to the database and provides the computed
     * result.
     *
     * @param handler the handler that is executed within the context of the transaction. The
     *        handler will be called once and its return value is returned from the transaction.
     * @param <T> the return type of the handler
     * @return the object that is returned by the given handler
     * @throws DatabaseException if an error occurs in the given handler function
     */
    public <T> T writeTransactionAndProvide(
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
     * @throws DatabaseException if an error occurs in the given handler function
     */
    public void writeTransaction(
            CheckedConsumer<? super DSLContext, ? extends DataAccessException> handler) {
        writeTransactionAndProvide(dsl -> {
            handler.accept(dsl);
            // noinspection ReturnOfNull
            return null;
        });
    }

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
