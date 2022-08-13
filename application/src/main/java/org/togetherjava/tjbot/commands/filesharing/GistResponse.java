package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
final class GistResponse {
    @JsonProperty("html_url")
    private String htmlUrl;

    public @NotNull String getHtmlUrl() {
        return this.htmlUrl;
    }

    public void setHtmlUrl(@NotNull String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }
}
