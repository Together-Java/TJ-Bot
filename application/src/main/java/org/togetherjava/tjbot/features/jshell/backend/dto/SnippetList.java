package org.togetherjava.tjbot.features.jshell.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;
import java.util.Objects;

public record SnippetList(List<String> snippets) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SnippetList {
        Objects.requireNonNull(snippets);
    }
}
