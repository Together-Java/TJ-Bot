package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
record GistRequest(@NotNull String description, @JsonProperty("public") boolean isPublic,
        @NotNull GistFiles files) {
}
