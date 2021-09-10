package org.togetherjava.tjbot.db;


import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteDataSource;
import org.togetherjava.tjbot.util.CheckedConsumer;
import org.togetherjava.tjbot.util.CheckedFunction;

import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The main database class.
 */
public class Database {

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
        sqliteConfig.setJournalMode(JournalMode.WAL);

        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl(jdbcUrl);

        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("/db").load();
        flyway.migrate();

        this.dslContext = DSL.using(dataSource.getConnection(), SQLDialect.SQLITE);
    }

    /**
     * Acquires read access to the database.
     *
     * @return a way to interact with the database in a read only way
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public <T> T read(CheckedFunction<DSLContext, T, DataAccessException> action) {
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
    public void read(CheckedConsumer<DSLContext, DataAccessException> action) {
        read(ctx -> {
            action.accept(ctx);
            return null;
        });
    }

    /**
     * Acquires read and write access to the database.
     *
     * @return a way to interact with the database
     * @throws DatabaseException if an error occurs in the passed handler function
     */
    public <T> T write(CheckedFunction<DSLContext, T, DataAccessException> action) {
        try {
            writeLock.lock();
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
    public void write(CheckedConsumer<DSLContext, DataAccessException> action) {
        write(ctx -> {
            action.accept(ctx);
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
    public <T> T readTransaction(CheckedFunction<DSLContext, T, DataAccessException> handler) {
        var holder = new Object() {
            T result;
        };

        try {
            getDslContext().transaction(cfg -> holder.result = handler.accept(cfg.dsl()));
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
    public void readTransaction(CheckedConsumer<DSLContext, DataAccessException> handler) {
        readTransaction(db -> {
            handler.accept(db);
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
    public <T> T writeTransaction(CheckedFunction<DSLContext, T, DataAccessException> handler) {
        var holder = new Object() {
            T result;
        };

        try {
            writeLock.lock();
            getDslContext().transaction(cfg -> holder.result = handler.accept(cfg.dsl()));
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
    public void writeTransaction(CheckedConsumer<DSLContext, DataAccessException> handler) {
        writeTransaction(db -> {
            handler.accept(db);
            return null;
        });
    }

    /**
     * @return the database dsl context
     */
    private DSLContext getDslContext() {
        return dslContext;
    }
}

