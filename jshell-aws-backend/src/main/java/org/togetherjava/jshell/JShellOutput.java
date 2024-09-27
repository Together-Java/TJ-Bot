package org.togetherjava.jshell;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A record containing the entire JShell output after evaluating code that was provided. This
 * contains the output of the standard output, error output and a list of all the snippets that were
 * evaluated during the execution.
 *
 * @param outputStream The output that was provided to the standard output.
 * @param errorStream The output that was provided to the error output.
 * @param events All the snippets that were evaluated during the execution.
 *
 * @author Suraj Kumar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JShellOutput(String outputStream, String errorStream, List<EvaluatedSnippet> events) {
}
