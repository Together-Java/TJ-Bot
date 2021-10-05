package org.togetherjava.logwatcher.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Basic Class for accumulating Information which is required to start this application
 */
@Configuration
public class Config {

    private static final AtomicReference<String> CONFIG_PATH = new AtomicReference<>();

    public static void init(final String pathToConfig) {
        if (!CONFIG_PATH.compareAndSet(null, pathToConfig)) {
            throw new IllegalStateException(
                    "Config Path already set to %s".formatted(CONFIG_PATH.get()));
        }
    }


    /**
     * Client-Name of the OAuth2-Application
     */
    private final String clientName;

    /**
     * Client-ID of the OAuth2-Application
     */
    private final String clientId;

    /**
     * Client-Secret of the OAuth2-Application
     */
    private final String clientSecret;

    /**
     * Discord-Username of the User to add as 1. User
     */
    private final String rootUserName;

    /**
     * Discord-ID of the User to add as 1. User
     */
    private final String rootDiscordID;

    /**
     * Path of the Log directory
     */
    private final String logPath;

    /**
     * Redirect Path for OAuth2
     */
    private final String redirectPath;

    /**
     * Path for this Database
     */
    private final String databasePath;

    public Config(final ObjectMapper mapper) {
        final JsonNode jsonNode = getJsonNode(mapper);

        this.clientName = jsonNode.get("clientName").asText();
        this.clientId = jsonNode.get("clientId").asText();
        this.clientSecret = jsonNode.get("clientSecret").asText();
        this.rootUserName = jsonNode.get("rootUserName").asText();
        this.rootDiscordID = jsonNode.get("rootDiscordID").asText();
        this.logPath = jsonNode.get("logPath").asText();
        this.redirectPath = jsonNode.get("redirectPath").asText();
        this.databasePath = jsonNode.get("databasePath").asText();
    }

    private JsonNode getJsonNode(final ObjectMapper mapper) {
        final String pathToConfig = Objects.requireNonNull(CONFIG_PATH.get(), "Path not Set");
        try {
            return mapper.readTree(Path.of(pathToConfig).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    public String getLogPath() {
        return logPath;
    }

    public String getRootUserName() {
        return rootUserName;
    }

    public String getRootDiscordID() {
        return rootDiscordID;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getDatabasePath() {
        return this.databasePath;
    }
}
