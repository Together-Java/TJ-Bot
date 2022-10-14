package org.togetherjava.tjbot.commands.search;

import io.mikael.urlbuilder.UrlBuilder;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * <p>{@code GoogleSearchStrategy} contains the logic for searching on Google.</p>
 * <p>The service is provided by <a href="https://serpapi.com/">Serpapi</a> is a wrapper over the Google API. This has
 *      been used because it provides us with much easier access to the Google API without having to go through the complicated
 *      process of setting up the application and auth on Google's side.</p>
 * @author <a href="https://github.com/surajkumar">Suraj Kumar</a>
 */
public class GoogleSearchStrategy extends SearchStrategy<HttpResponse<String>> {
    /** The API key to provide authentication into Serpapi. */
    private final String apiKey;

    /** The Serpapi REST service URL used for fetching Google search results. */
    private static final String API_ENDPOINT = "https://serpapi.com/search";

    /** The HttpClient object used for sending the REST API calls. */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GoogleSearchStrategy(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * <p>Called the Serpapi API with the provided search term async.</p>
     * <p>The result of the API is a JSON body that can be used to grab all the information we need regarding the search and
     * search contents.</p>
     * <p>The Serpapi takes 2 parameters:<br>
     * <ol>
     * <li>q: The search query</li>
     * <li>api_key: The authentication key created via the Serpapi dashboard.</li>
     * </ol></p>
     * For additional parameters see: <a href="https://serpapi.com/search-api">Serpapi API docs</a>
     * @param searchTerm The search term to query against. This would be in the exact format a user would be searching
     *                   on Google.
     * @return           A CompletableFuture as this action is blocking.
     */
    @Override
    public CompletableFuture<HttpResponse<String>> search(String searchTerm) {
        return httpClient.sendAsync(
                HttpRequest
                    .newBuilder(UrlBuilder.fromString(API_ENDPOINT)
                        .addParameter("q", searchTerm)
                        .addParameter("api_key", apiKey)
                        .toUri())
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}