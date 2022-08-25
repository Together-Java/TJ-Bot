package org.togetherjava.tjbot.website;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring boot application to serve the bots welcome webpage.
 */
@SpringBootApplication
public class Application {
    /**
     * Starts the application.
     *
     * @param args Not supported
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
