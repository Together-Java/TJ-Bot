package org.togetherjava.logwatcher.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Basic Class for accumulating Information which is required to start this application
 */
@Configuration
public class Config {

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

    public Config() {
        final JsonNode jsonNode = getJsonNode();

        this.clientName = jsonNode.get("clientName").asText();
        this.clientId = jsonNode.get("clientId").asText();
        this.clientSecret = jsonNode.get("clientSecret").asText();
        this.rootUserName = jsonNode.get("rootUserName").asText();
        this.rootDiscordID = jsonNode.get("rootDiscordID").asText();
        this.logPath = jsonNode.get("logPath").asText();
        this.redirectPath = jsonNode.get("redirectPath").asText();
    }

    private JsonNode getJsonNode() {
        final String prop =
                Objects.requireNonNull(System.getProperty("TJ_CONFIG_PATH"), "Property");
        try {
            return new ObjectMapper().readTree(Path.of(prop).toFile());
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

}
