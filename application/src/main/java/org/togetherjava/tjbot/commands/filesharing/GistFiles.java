package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.Map;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
record GistFiles(@JsonAnyGetter Map<String, GistFile> nameToContent) {
}
