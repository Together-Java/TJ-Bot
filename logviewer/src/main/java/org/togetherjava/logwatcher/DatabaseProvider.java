package org.togetherjava.logwatcher;

import org.slf4j.LoggerFactory;
import org.springframework.beans.FatalBeanException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.togetherjava.tjbot.db.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class DatabaseProvider {

    private static final AtomicReference<Database> ATOM_DB = new AtomicReference<>();
    private static final Lock latch = new ReentrantLock();

    public DatabaseProvider() {
        try {
            latch.lock();
            if (ATOM_DB.get() != null) {
                return;
            }

            ATOM_DB.set(createDB());
        } finally {
            latch.unlock();
        }
    }

    @SuppressWarnings({"java:S2139"}) // At this point there is nothing we can do about a
    // SQLException
    private Database createDB() {
        final Path dbPath = getDBPath();

        try {
            return new Database("jdbc:sqlite:%s".formatted(dbPath.toAbsolutePath()));
        } catch (final SQLException e) {
            LoggerFactory.getLogger(DatabaseProvider.class)
                .error("Exception while creating Database.", e);
            throw new FatalBeanException("Could not create Database.", e);
        }
    }

    private Path getDBPath() {
        final Path dbPath = Path.of("./logviewer/db/db.db");

        try {
            Files.createDirectories(dbPath.getParent());
        } catch (final IOException e) {
            LoggerFactory.getLogger(DatabaseProvider.class)
                .error("Exception while creating Database-Path.", e);
        }

        return dbPath;
    }


    @Bean
    public Database getDb() {
        return ATOM_DB.get();
    }


}
