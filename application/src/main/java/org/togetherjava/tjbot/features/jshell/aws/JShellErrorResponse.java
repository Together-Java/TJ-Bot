package org.togetherjava.tjbot.features.jshell.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a response from JShell that contains an error key.
 *
 * @author Suraj Kuamr
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JShellErrorResponse(@JsonProperty("error") String error) {
}
