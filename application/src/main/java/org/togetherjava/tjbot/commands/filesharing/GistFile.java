package org.togetherjava.tjbot.commands.filesharing;

import org.jetbrains.annotations.NotNull;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
final class GistFile {

    private @NotNull String content;

    public GistFile(@NotNull String content) {
        this.content = content;
    }

    public @NotNull String getContent() {
        return this.content;
    }
}
