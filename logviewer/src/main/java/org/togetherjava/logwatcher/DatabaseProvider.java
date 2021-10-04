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

@Configuration
public class DatabaseProvider {

    private static final AtomicReference<Database> AtomDb = new AtomicReference<>();

    public DatabaseProvider() {
        synchronized (AtomDb) {
            if (AtomDb.get() != null) {
                return;
            }

            AtomDb.set(createDB());
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
            if (Files.notExists(dbPath.getParent())) {
                Files.createDirectories(dbPath.getParent());
            }
        } catch (final IOException e) {
            LoggerFactory.getLogger(DatabaseProvider.class)
                .error("Exception while creating Database-Path.", e);
        }

        return dbPath;
    }


    @Bean
    public Database getDB() {
        return AtomDb.get();
    }


}
