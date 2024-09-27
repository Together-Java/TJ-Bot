package org.togetherjava.jshell;

import java.util.List;

/**
 * A record containing the JShell snippet evaluation. A snippet in the context of JShell refers to a
 * statement in Java.
 *
 * @param statement The statement that was executed
 * @param status The status returned by JShell
 * @param value The value returned for the executed snippet
 * @param diagnostics The diagnostic provided by JShell, usually this contains errors such as syntax
 *
 * @author Suraj Kumar
 */
public record EvaluatedSnippet(String statement, String status, String value,
        List<String> diagnostics) {
}
