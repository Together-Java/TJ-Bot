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

// final class GistRequest {
//
// private String description;
// @JsonProperty("public")
// private boolean isPublic;
// private GistFiles files;
//
// public GistRequest(@NotNull String description, @NotNull boolean isPublic,
// @NotNull GistFiles files) {
// this.description = description;
// this.isPublic = isPublic;
// this.files = files;
// }
//
// public @NotNull String getDescription() {
// return description;
// }
//
// public @NotNull boolean isPublic() {
// return isPublic;
// }
//
// public @NotNull GistFiles getFiles() {
// return files;
// }
// }


