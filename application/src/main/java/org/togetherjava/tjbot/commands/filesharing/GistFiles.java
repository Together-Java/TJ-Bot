package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
final class GistFiles {

    @JsonIgnore
    private Map<String, GistFile> nameToContent;

    public GistFiles(@NotNull Map<String, GistFile> nameToContent) {
        this.nameToContent = nameToContent;
    }

    @JsonAnyGetter
    public @NotNull Map<String, GistFile> getFiles() {
        return nameToContent;
    }
}