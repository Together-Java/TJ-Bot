package org.togetherjava.jshell;

import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.togetherjava.jshell.exceptions.JShellEvaluationException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * This class is used to interface with the JShell CLI. Its sole role is to interact with JShell and
 * handle running commands and returning the output.
 *
 * @author Suraj Kumar
 */
public class JShellService {
    private static final Logger LOGGER = LogManager.getLogger(JShellService.class);

    /**
     * This method is used to run a snippet of Java code using JShell. The method runs async so the
     * result is captured in a CompletableFuture containing the JShellOutput result.
     *
     * @param snippet The Java code that is to be evaluated by JShell.
     * @return A Future containing the output of the evaluated code.
     * @throws RuntimeException When there was an error running JShell or an exception occured
     *         during code evaluation.
     */
    public CompletableFuture<JShellOutput> executeJShellSnippet(String snippet)
            throws JShellEvaluationException {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Running snippet {}", snippet);

            try (OutputStream outputStream = new ByteArrayOutputStream();
                    OutputStream errorStream = new ByteArrayOutputStream();

                    JShell jshell = JShell.builder()
                        .out(new PrintStream(outputStream))
                        .err(new PrintStream(errorStream))
                        .build()) {


                List<EvaluatedSnippet> events = new ArrayList<>();
                String next = snippet;

                while (!next.isEmpty()) {
                    SourceCodeAnalysis.CompletionInfo completionInfo =
                            jshell.sourceCodeAnalysis().analyzeCompletion(next);
                    LOGGER.debug("completionInfo: {}", completionInfo);

                    List<SnippetEvent> evalEvents = jshell.eval(completionInfo.source());
                    LOGGER.debug("evalEvents: {}", evalEvents);

                    for (SnippetEvent event : evalEvents) {
                        String statement = event.snippet()
                            .toString()
                            .substring(event.snippet().toString().indexOf("-") + 1)
                            .trim();
                        String status = event.status().toString();
                        String value = event.value();

                        List<String> diagnostics = new ArrayList<>();

                        if (status.equals("REJECTED")) {
                            diagnostics.addAll(jshell.diagnostics(event.snippet())
                                .map(diag -> clean(diag.getMessage(Locale.ENGLISH))
                                    .replace("\\", "\\\\")
                                    .replace("\n", "\\n"))
                                .toList());
                            events.add(new EvaluatedSnippet(statement, status, value, diagnostics));
                            break;
                        }

                        events.add(new EvaluatedSnippet(statement, status, value, diagnostics));
                        LOGGER.debug("Added event: {}", event);
                    }

                    next = completionInfo.remaining();
                }

                String output = clean(outputStream.toString());
                String error = clean(errorStream.toString());

                LOGGER.debug("JShell output stream: {}", output);
                LOGGER.debug("JShell error stream: {}", error);

                return new JShellOutput(output, error, events);
            } catch (Exception e) {
                LOGGER.error("Failure while running JShell Snippet", e);
                throw new JShellEvaluationException(e.getMessage());
            }
        }, Executors.newCachedThreadPool());
    }

    private static String clean(String input) {
        return input.replace("\r", "").trim();
    }
}
