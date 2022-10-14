package org.togetherjava.tjbot.commands.search;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class StackOverflowSearchStrategy extends SearchStrategy {
    private static final String API_URL =
            "https://api.stackexchange.com/2.3/questions?order=desc&sort=activity&site=stackoverflow&intitle=";

    @Override
    public CompletableFuture<HttpResponse<String>> search(String searchTerm) {
        return null;
    }
}
