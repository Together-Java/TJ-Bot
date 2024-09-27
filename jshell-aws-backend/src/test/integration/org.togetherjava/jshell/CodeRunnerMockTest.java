package org.togetherjava.jshell;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for the CodeRunner Lambda function. The way these tests work is by spinning up
 * a "mock" web server that acts as if it was the AWS API Gateway. Then, our local web server
 * creates a proxy to the Lambda handler and handles the request/response.
 * <p>
 * These tests are primarily targeted at the web portion of the code runner.
 *
 * @author Suraj Kumar
 */
@Disabled // CodeQL can't run this test, but we have adequate unit tests
class CodeRunnerMockTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int HTTP_PORT = 3001;
    private static final String API_URL = "http://localhost:%d/".formatted(HTTP_PORT);
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Create a mock web server to pass HTTP requests to the Lambda handler.
     *
     * @throws IOException When we fail to create the web server or the port is in use.
     */
    @BeforeAll
    static void setupServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        server.createContext("/", new HttpLambdaProxy());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    /**
     * HTTP handler class that takes incoming requests from the mock web server and passes it to the
     * Lambda handler
     */
    static class HttpLambdaProxy implements HttpHandler {
        private final CodeRunner codeRunner = new CodeRunner();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            OutputStream outputStream = exchange.getResponseBody();

            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();

            event.setBody(new String(exchange.getRequestBody().readAllBytes()));

            APIGatewayProxyResponseEvent response =
                    codeRunner.handleRequest(event, mock(Context.class));

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.getStatusCode(), response.getBody().length());

            outputStream.write(response.getBody().getBytes());
            outputStream.flush();
            outputStream.close();
        }
    }

    /**
     * The request to send to the CodeRunner in the structure that it expects
     */
    record CodeRequest(String code) {
    }

    @ParameterizedTest(name = "{index} => code={0}, expectedStatus={1}, expectedBody={2}")
    @CsvSource({
            "System.out.println(\"hello\");, 200, '{\"outputStream\":\"hello\",\"errorStream\":\"\",\"events\":[{\"statement\":\"System.out.println(\\\"hello\\\");\",\"status\":\"VALID\",\"value\":\"\",\"diagnostics\":[]}]}'",
            "Thread.sleep(15001);, 408, '{\"error\":\"JShell execution timed out\"}'",
            "'', 400, '{\"error\":\"Code field is empty or invalid\"}'"})
    void shouldRespondCorrectlyBasedOnCode(String code, int expectedStatus, String expectedBody)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(API_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers
                .ofString(OBJECT_MAPPER.writeValueAsString(new CodeRequest(code))))
            .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(expectedStatus, response.statusCode());
        assertEquals(expectedBody, response.body());
    }
}
