package org.togetherjava.tjbot.features.chatgpt.tools.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Subset of the Tavily search API JSON response that {@link WebSearchTool} cares about. Unknown
 * fields are ignored so the API can evolve without breaking deserialization.
 *
 * @param query the search query echoed back by Tavily
 * @param answer Tavily's pre-synthesized natural-language answer when {@code include_answer} is
 *        requested; may be empty
 * @param results ranked list of source pages backing the answer
 * @param responseTime the search latency, in seconds, reported by Tavily
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TavilyResponse(@JsonProperty("query") String query,
        @JsonProperty("answer") String answer, @JsonProperty("results") List<Result> results,
        @JsonProperty("response_time") double responseTime) {

    /**
     * Single search result entry inside a {@link TavilyResponse}.
     *
     * @param url canonical URL of the source page
     * @param title page title, as extracted by Tavily
     * @param content excerpted page content relevant to the query
     * @param score Tavily's relevance score, higher is more relevant
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("url") String url, @JsonProperty("title") String title,
            @JsonProperty("content") String content, @JsonProperty("score") double score) {
    }
}
