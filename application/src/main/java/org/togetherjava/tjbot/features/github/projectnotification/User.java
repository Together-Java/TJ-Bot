package org.togetherjava.tjbot.features.github.projectnotification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This record represents an author of a pull request
 * 
 * @param name The GitHub username of the PR author
 * @param avatarUrl The GitHub users profile picture/avatar URL
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record User(@JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl) {
}
