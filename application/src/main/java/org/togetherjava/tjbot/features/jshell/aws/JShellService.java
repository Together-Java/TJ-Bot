package org.togetherjava.tjbot.features.jshell.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.togetherjava.tjbot.features.jshell.aws.exceptions.JShellAPIException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The JShellService class is used to interact with the AWS JShell API.
 *
 * @author Suraj Kumar
 */
public class JShellService {
    private static final Logger LOGGER = LogManager.getLogger(JShellService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String apiURL;
    private final HttpClient httpClient;

    /**
     * Constructs a JShellService.
     *
     * @param apiURl The Lambda Function URL to send API requests to
     */
    public JShellService(String apiURl) {
        this.apiURL = apiURl;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Sends an HTTP request to the AWS JShell API.
     *
     * @param jShellRequest The request object containing the code to evaluate
     * @return The API response as a JShellResponse object
     * @throws URISyntaxException If the API URL is invalid
     * @throws JsonProcessingException If the API response failed to get parsed by Jackson to our
     *         mapping.
     */
    public JShellResponse sendRequest(JShellRequest jShellRequest)
            throws URISyntaxException, JsonProcessingException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(apiURL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers
                .ofString(OBJECT_MAPPER.writeValueAsString(jShellRequest)))
            .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new JShellAPIException(response.statusCode(), response.body());
            }

            String body = response.body();
            LOGGER.trace("Received the following body from the AWS JShell API: {}", body);

            return OBJECT_MAPPER.readValue(response.body(), JShellResponse.class);

        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to send http request to the AWS JShell API", e);
            Thread.currentThread().interrupt();
        }

        return null;
    }
}
