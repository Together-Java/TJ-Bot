package org.togetherjava.tjbot.commands.filesharing;

import org.jetbrains.annotations.NotNull;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
record GistFile(@NotNull String content) {
}
