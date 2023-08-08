package org.togetherjava.tjbot.features.jshell.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;
import java.util.Objects;

/**
 * List of snippets returned by snippets endpoint.
 * 
 * @param snippets the list of snippets
 */
public record SnippetList(List<String> snippets) {
    /**
     * List of snippets returned by snippets endpoint.
     * 
     * @param snippets the list of snippets
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SnippetList {
        Objects.requireNonNull(snippets);
    }
}
