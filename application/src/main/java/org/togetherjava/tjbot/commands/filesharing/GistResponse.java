package org.togetherjava.tjbot.commands.filesharing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nonnull;

/**
 * @see <a href="https://docs.github.com/en/rest/gists/gists#create-a-gist">Create a Gist via
 *      API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
final class GistResponse {
    @JsonProperty("html_url")
    private String htmlUrl;

    @Nonnull
    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }
}
