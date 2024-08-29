package org.togetherjava.tjbot.features.jshell.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A record containing the AWS JShell API response.
 *
 * @param errorStream The content in JShells error stream
 * @param outputStream The content in JShells standard output stream
 * @param events A list of snippets that were evaluated
 *
 * @author Suraj Kumar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JShellResponse(@JsonProperty("errorStream") String errorStream,
        @JsonProperty("outputStream") String outputStream,
        @JsonProperty("events") List<JShellSnippet> events) {
}
