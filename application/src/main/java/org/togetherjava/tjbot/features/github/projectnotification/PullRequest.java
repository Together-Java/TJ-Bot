package org.togetherjava.tjbot.features.github.projectnotification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * This record is a container for the pull request information received when calling the GitHub pull
 * requests endpoint.
 *
 * @param htmlUrl The user-friendly link to the PR
 * @param number The pull request number
 * @param state The state that the PR is in for example "opened", "closed", "merged"
 * @param title The title of the PR
 * @param user The user object representing the pull request author
 * @param body The PR description
 * @param createdAt The time that the PR was created
 * @param draft True if the PR is in draft otherwise false
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequest(@JsonProperty("html_url") String htmlUrl,
        @JsonProperty("number") int number, @JsonProperty("state") String state,
        @JsonProperty("title") String title, @JsonProperty("user") User user,
        @JsonProperty("body") String body, @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("draft") boolean draft) {
}
