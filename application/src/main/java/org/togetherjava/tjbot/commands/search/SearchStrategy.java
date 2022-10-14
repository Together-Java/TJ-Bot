package org.togetherjava.tjbot.commands.search;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public abstract class SearchStrategy {
    public abstract CompletableFuture<HttpResponse<String>> search(String searchTerm);
}
