package org.togetherjava.tjbot.features.chatgpt.tools.web;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.togetherjava.tjbot.features.chatgpt.tools.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Example tool: searches the live web via the <a href="https://docs.tavily.com/">Tavily search
 * API</a>. Requires a Tavily API key passed at construction (typically wired in from
 * {@code Config}).
 */
public final class WebSearchTool implements AiTool<TavilyResponse> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private static final String QUERY_PARAM = "query";
    private static final int MAX_RESULTS = 5;

    private final String apiKey;

    /**
     * Creates the tool with the Tavily API key it should authenticate with.
     *
     * @param apiKey Tavily API key; if {@code null} or blank, every {@link #run(java.util.Map)
     *        invocation} returns a failure result rather than attempting the HTTP call
     */
    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return """
                Search the live web for up-to-date information.
                Use this whenever you need current facts (news, prices, schedules, anything that can change).
                """;
    }

    @Override
    public List<AiToolParameter> parameters() {
        return List.of(new AiToolParameter(QUERY_PARAM, "The search query, in natural language",
                AiToolParameterType.STRING, true));
    }

    @Override
    public AiToolResult<TavilyResponse> run(Map<String, String> arguments) {
        String query = arguments.get(QUERY_PARAM);
        if (query == null || query.isBlank()) {
            return AiToolResult.failed("Missing required parameter: " + QUERY_PARAM);
        }
        if (apiKey.isBlank()) {
            return AiToolResult.failed("Tavily API key is not configured");
        }

        Map<String, Object> body = Map.of("api_key", apiKey, QUERY_PARAM, query, "max_results",
                MAX_RESULTS, "search_depth", "basic", "include_answer", true);

        HttpResponse<String> response;
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TAVILY_URL))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return AiToolResult.failed("Web search interrupted");
        } catch (IOException e) {
            return AiToolResult.failed("Failed to send request: " + e.getMessage());
        }

        if (response.statusCode() != 200) {
            return AiToolResult.failed("Tavily returned HTTP " + response.statusCode());
        }

        try {
            return AiToolResult.ok(OBJECT_MAPPER.readValue(response.body(), TavilyResponse.class));
        } catch (IOException e) {
            return AiToolResult.failed("Failed to parse Tavily response: " + e.getMessage());
        }
    }
}
