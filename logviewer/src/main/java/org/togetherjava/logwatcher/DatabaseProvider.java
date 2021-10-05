package org.togetherjava.logwatcher;

import org.slf4j.LoggerFactory;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.togetherjava.tjbot.db.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

@Configuration
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class DatabaseProvider {

    private final Database db;

    public DatabaseProvider() {
        this.db = createDB();
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
        return this.db;
    }


}
