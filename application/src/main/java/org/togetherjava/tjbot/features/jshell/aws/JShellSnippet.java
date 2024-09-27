package org.togetherjava.tjbot.features.jshell.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A JShell snippet is a statement that is to be executed. This record is used to hold information
 * about a statement that was provided by the AWS JShell API
 *
 * @param statement The statement that was executed
 * @param value The return value of the statement
 * @param status The status from evaluating the statement e.g. "VALID", "INVALID"
 * @param diagnostics A list of diagnostics such as error messages provided by JShell
 *
 * @author Suraj Kumar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JShellSnippet(@JsonProperty("statement") String statement,
        @JsonProperty("value") String value, @JsonProperty("status") String status,
        List<String> diagnostics) {
}
