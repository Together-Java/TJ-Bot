package org.togetherjava.tjbot.commands.search;

import io.mikael.urlbuilder.UrlBuilder;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class GoogleSearchStrategy extends SearchStrategy {
    private static final String API_KEY =
            "1d84500b082341aad66de5cf02f7c83d8608411b01b7ffcbb2d40042eea250df";
    private static final String API_ENDPOINT = "https://serpapi.com/search";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public CompletableFuture<HttpResponse<String>> search(String searchTerm) {
        return httpClient.sendAsync(
                HttpRequest
                    .newBuilder(UrlBuilder.fromString(API_ENDPOINT)
                        .addParameter("q", searchTerm)
                        .addParameter("api_key", API_KEY)
                        .toUri())
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
