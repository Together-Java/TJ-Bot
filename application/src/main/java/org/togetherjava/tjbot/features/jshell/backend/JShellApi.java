package org.togetherjava.tjbot.features.jshell.backend;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.SnippetList;
import org.togetherjava.tjbot.features.utils.RequestFailedException;
import org.togetherjava.tjbot.features.utils.ResponseUtils;
import org.togetherjava.tjbot.features.utils.UncheckedRequestFailedException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;

public class JShellApi {
    public static final int SESSION_NOT_FOUND = 404;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    public JShellApi(ObjectMapper objectMapper, String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;

        this.httpClient = HttpClient.newBuilder().build();
    }

    public JShellResult evalOnce(String code) throws RequestFailedException {
        return send(baseUrl + "single-eval",
                HttpRequest.newBuilder().POST(BodyPublishers.ofString(code)),
                ResponseUtils.ofJson(JShellResult.class, objectMapper)).body();
    }

    public JShellResult evalSession(String code, String sessionId) throws RequestFailedException {
        return send(baseUrl + "eval/" + sessionId,
                HttpRequest.newBuilder().POST(BodyPublishers.ofString(code)),
                ResponseUtils.ofJson(JShellResult.class, objectMapper)).body();
    }

    public SnippetList snippetsSession(String sessionId) throws RequestFailedException {
        return send(baseUrl + "snippets/" + sessionId, HttpRequest.newBuilder().GET(),
                ResponseUtils.ofJson(SnippetList.class, objectMapper)).body();
    }

    public void closeSession(String sessionId) throws RequestFailedException {
        send(baseUrl + sessionId, HttpRequest.newBuilder().DELETE(), BodyHandlers.discarding())
            .body();
    }

    private <T> HttpResponse<T> send(String url, HttpRequest.Builder builder, BodyHandler<T> body)
            throws RequestFailedException {
        try {
            HttpResponse<T> response = httpClient.send(builder.uri(new URI(url)).build(), body);
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                return response;
            }
            throw new RequestFailedException("Request failed with status: " + response.statusCode(),
                    response.statusCode());
        } catch (IOException e) {
            if (e.getCause() instanceof UncheckedRequestFailedException r) {
                throw r.toChecked();
            }
            throw new UncheckedIOException(e);
        } catch (URISyntaxException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
