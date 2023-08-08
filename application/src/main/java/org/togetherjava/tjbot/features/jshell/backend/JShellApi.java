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

/**
 * Allows to interact with the unofficial JShell REST API of the Together-Java JShell backend
 * project.
 * <p>
 * Each method may do a blocking HTTP request and may throw a RequestFailedException if status code
 * isn't 200 or 204.
 * <p>
 * When startup script boolean argument is asked, true means {@link JShellApi#STARTUP_SCRIPT_ID} and
 * false means Together-Java JShell backend's default startup script.
 * <p>
 * For more information, check the Together-Java JShell backend project.
 */
public class JShellApi {
    public static final int SESSION_NOT_FOUND = 404;
    /**
     * The startup script to use when startup script boolean argument is true.
     */
    private static final String STARTUP_SCRIPT_ID = "CUSTOM_DEFAULT";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    /**
     * Creates a JShellAPI
     * 
     * @param objectMapper the json mapper to use
     * @param baseUrl the base url of the JShell REST API
     */
    public JShellApi(ObjectMapper objectMapper, String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;

        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * Evaluates the code in a one time only session, will block until the request is over.
     * 
     * @param code the code to evaluate
     * @param startupScript if the {@link JShellApi#STARTUP_SCRIPT_ID startup script} should be
     *        executed at the start of the session
     * @return the result of the evaluation
     * @throws RequestFailedException if the status code is not 200 or 204
     */
    public JShellResult evalOnce(String code, boolean startupScript) throws RequestFailedException {
        return send(
                baseUrl + "single-eval"
                        + (startupScript ? "?startupScriptId=" + STARTUP_SCRIPT_ID : ""),
                HttpRequest.newBuilder().POST(BodyPublishers.ofString(code)),
                ResponseUtils.ofJson(JShellResult.class, objectMapper)).body();
    }

    /**
     * Evaluates the code in a regular session, will block until the request is over.
     * 
     * @param code the code to evaluate
     * @param startupScript if the {@link JShellApi#STARTUP_SCRIPT_ID startup script} should be
     *        executed at the start of the session
     * @return the result of the evaluation
     * @throws RequestFailedException if the status code is not 200 or 204
     */
    public JShellResult evalSession(String code, String sessionId, boolean startupScript)
            throws RequestFailedException {
        return send(
                baseUrl + "eval/" + sessionId
                        + (startupScript ? "?startupScriptId=" + STARTUP_SCRIPT_ID : ""),
                HttpRequest.newBuilder().POST(BodyPublishers.ofString(code)),
                ResponseUtils.ofJson(JShellResult.class, objectMapper)).body();
    }

    /**
     * Gets and return the snippets for the given session id, will block until the request is over.
     * 
     * @param sessionId the id of the session to get the snippets from
     * @param includeStartupScript if the startup script should be included in the returned snippets
     * @return the snippets of the session
     * @throws RequestFailedException if the status code is not 200 or 204
     */
    public SnippetList snippetsSession(String sessionId, boolean includeStartupScript)
            throws RequestFailedException {
        return send(
                baseUrl + "snippets/" + sessionId + "?includeStartupScript=" + includeStartupScript,
                HttpRequest.newBuilder().GET(),
                ResponseUtils.ofJson(SnippetList.class, objectMapper)).body();
    }

    /**
     * Closes the given session.
     * 
     * @param sessionId the id of the session to close
     * @throws RequestFailedException if the status code is not 200 or 204
     */
    public void closeSession(String sessionId) throws RequestFailedException {
        send(baseUrl + sessionId, HttpRequest.newBuilder().DELETE(), BodyHandlers.discarding())
            .body();
    }

    /**
     * Gets and return the {@link JShellApi#STARTUP_SCRIPT_ID startup script}, will block until the
     * request is over.
     * 
     * @return the startup script
     * @throws RequestFailedException if the status code is not 200 or 204
     */
    public String startupScript() throws RequestFailedException {
        return send(baseUrl + "startup_script/" + STARTUP_SCRIPT_ID, HttpRequest.newBuilder().GET(),
                BodyHandlers.ofString()).body();
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
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
