package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
record GistFiles(@NotNull @JsonAnyGetter Map<String, GistFile> nameToContent) {
}
