package org.togetherjava.jshell;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.togetherjava.jshell.exceptions.JShellEvaluationException;
import org.togetherjava.jshell.exceptions.JShellTimeoutException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class is the main entry point used by AWS Lambda. When HTTP requests comes to AWS, this
 * Lambda handler is invoked. The role of this command is to take the incoming request data and
 * transform it into a suitable payload for the JShell Service then return the results as an
 * appropriate JSON HTTP response.
 * <p>
 * When a request is made to the API, the request JSON is expected to look similar to the following
 * example: <code>
 *     { "code": "System.out.println(\"Hello, World!\");" }
 * </code>
 *
 * @author Suraj Kumar
 */
@SuppressWarnings("unused") // suppressed as the usage is outside the application
public class CodeRunner
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger LOGGER = LogManager.getLogger(CodeRunner.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JShellService JSHELL_SERVICE = new JShellService();

    /**
     * Represents the deserialized request body that came into this handler.
     *
     * @param code the "code" that is to be evaluated by JShell
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Request(@JsonProperty("code") String code) {
    }

    /**
     * This record represents the HTTP request that came into this Lambda. AWS provides additional
     * information such as request headers, method etc. But it also provides AWS specific data that
     * we don't want or need. Hence, why this record only contains the "body" that came with the
     * request.
     *
     * @param body The body that came with the HTTP request
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(@JsonProperty("body") String body) {
    }

    /**
     * This record is used to represent an error response sent as a response to the request made.
     *
     * @param error The error message to display
     */
    record ErrorResponse(String error) {
    }

    /**
     * Handles incoming requests for the Lambda function. This method parses the request, sends the
     * requested code to be evaluated by the JShell service, and returns the result back to the API
     * caller.
     *
     * @param event The incoming event to this Lambda
     * @param context An AWS Lambda context object for accessing metadata and environment details
     * @return APIGatewayProxyResponseEvent which contains the HTTP status code and body
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event,
            Context context) {
        LOGGER.trace("Handling request for function: {}", context.getFunctionName());

        String requestData = event.getBody();

        if (requestData == null) {
            return respondWith(400,
                    serializeObject(new ErrorResponse("Failed to read the request stream")));
        }

        Request request = parseRequest(requestData);

        if (request == null || request.code().isBlank()) {
            return respondWith(400,
                    serializeObject(new ErrorResponse("Code field is empty or invalid")));
        }

        try {
            JShellOutput jShellOutput = executeCode(request);
            return respondWith(200, serializeObject(jShellOutput));
        } catch (JShellTimeoutException e) {
            return respondWith(408, serializeObject(new ErrorResponse(e.getMessage())));
        } catch (JShellEvaluationException e) {
            return respondWith(500, serializeObject(new ErrorResponse(e.getMessage())));
        }
    }

    private static APIGatewayProxyResponseEvent respondWith(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setBody(body);
        response.setHeaders(Map.of("Content-Type", "application/json"));
        return response;
    }

    /**
     * Reads the input stream and returns it as a String.
     *
     * @param input The InputStream to read from
     * @return The request data as a String, or null if an error occurs
     */
    private String readInputStream(InputStream input) {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Error reading input stream", e);
            return null;
        }
    }

    /**
     * Parses the request data into a Payload object.
     *
     * @param data The request data as a String
     * @return The parsed Payload object, or null if parsing fails
     */
    private Payload parsePayload(String data) {
        try {
            return OBJECT_MAPPER.readValue(data, Payload.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing request payload", e);
            return null;
        }
    }

    /**
     * Parses the Payload object to obtain the Request object.
     *
     * @param payload The Payload object
     * @return The parsed Request object, or null if parsing fails
     */
    private Request parseRequest(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, Request.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing request body", e);
            return null;
        }
    }

    /**
     * Executes the provided code using the JShell service and returns the result.
     *
     * @param request The Request object containing the code to execute
     * @return The result of the code execution as a JShellOutput object
     */
    private JShellOutput executeCode(Request request) {
        try {
            CompletableFuture<JShellOutput> futureOutput =
                    JSHELL_SERVICE.executeJShellSnippet(request.code());
            return futureOutput.get(15L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new JShellTimeoutException("JShell execution timed out");
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new JShellEvaluationException(
                    "Error executing JShell snippet: " + e.getMessage());
        }
    }

    /**
     * Converts the provided object into a JSON string. On serialization exceptions, this method
     * will return an empty JSON string.
     *
     * @param object The object to convert to JSON
     * @return A JSON String representing the provided object
     */
    private static String serializeObject(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (IOException e) {
            LOGGER.error("Error serializing output", e);
            return "{}"; // Return an empty JSON object in case of serialization failure
        }
    }
}
