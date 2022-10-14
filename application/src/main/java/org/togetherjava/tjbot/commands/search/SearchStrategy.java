package org.togetherjava.tjbot.commands.search;

import java.util.concurrent.CompletableFuture;

/**
 * <p>
 * A base class for a search Strategy. This allows us to implement multiple variations of a search functionality
 * without having to rewrite any of the main logic (except parsing) used for getting search results. For
 * example in the {@link GoogleSearchStrategy} the Serpapi API is used for fetching results from Google. If we were to
 * change this, we can do by creating a new SearchStrategy and replace the existing call with the new strategy
 * without further code changes.</p>
 * <p>Additionally, we now have flexibility to implement searching in other platforms such as Stackoverflow, Yahoo, Bing
 * etc.</p>
 * @author <a href="https://github.com/surajkumar">Suraj Kumar</a>
 */
public abstract class SearchStrategy<T> {

    /**
     * A common search function that should asynchronously handle a search function for a given term.
     *
     * @param searchTerm The search term to look for.
     * @return           A CompletableFuture for retrieval of the response when ready.
     */
    public abstract CompletableFuture<T> search(String searchTerm);
}