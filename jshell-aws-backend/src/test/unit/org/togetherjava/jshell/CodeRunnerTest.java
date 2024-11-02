package org.togetherjava.jshell;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@Disabled
class CodeRunnerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * The request to send to the CodeRunner in the structure that it expects
     */
    record CodeRequest(String code) {
    }

    @Test
    void shouldEvalHelloWorld() throws Exception {
        CodeRunner codeRunner = new CodeRunner();

        String codeSnippet = OBJECT_MAPPER.writeValueAsString(new CodeRequest("""
                System.out.println("Hello, World!");
                """));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(codeSnippet);

        APIGatewayProxyResponseEvent response =
                codeRunner.handleRequest(event, mock(Context.class));

        JShellOutput jShellOutput = OBJECT_MAPPER.readValue(response.getBody(), JShellOutput.class);

        assertEquals("Hello, World!", jShellOutput.outputStream());
    }

    @Test
    void shouldHaveErrorOutput() throws Exception {
        CodeRunner codeRunner = new CodeRunner();

        String codeSnippet = OBJECT_MAPPER.writeValueAsString(new CodeRequest("""
                System.err.println("Hello, World!");
                """));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(codeSnippet);

        APIGatewayProxyResponseEvent response =
                codeRunner.handleRequest(event, mock(Context.class));

        JShellOutput jShellOutput = OBJECT_MAPPER.readValue(response.getBody(), JShellOutput.class);

        assertEquals("Hello, World!", jShellOutput.errorStream());
    }

    @Test
    void shouldHaveBothErrorAndStdOutput() throws Exception {
        CodeRunner codeRunner = new CodeRunner();

        String codeSnippet = OBJECT_MAPPER.writeValueAsString(new CodeRequest("""
                System.out.println("Foo");
                System.err.println("Bar");
                """));


        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(codeSnippet);

        APIGatewayProxyResponseEvent response =
                codeRunner.handleRequest(event, mock(Context.class));

        JShellOutput jShellOutput = OBJECT_MAPPER.readValue(response.getBody(), JShellOutput.class);

        assertEquals("Foo", jShellOutput.outputStream());
        assertEquals("Bar", jShellOutput.errorStream());
    }

    @Test
    void shouldShowSyntaxErrorCause() throws Exception {
        CodeRunner codeRunner = new CodeRunner();

        String codeSnippet = OBJECT_MAPPER.writeValueAsString(new CodeRequest("""
                 int x = y
                """));


        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(codeSnippet);

        APIGatewayProxyResponseEvent response =
                codeRunner.handleRequest(event, mock(Context.class));

        JShellOutput jShellOutput = OBJECT_MAPPER.readValue(response.getBody(), JShellOutput.class);

        List<EvaluatedSnippet> evaluatedSnippets = jShellOutput.events();

        if (!evaluatedSnippets.isEmpty()) {
            List<String> diagnostics = evaluatedSnippets.getFirst().diagnostics();
            if (!diagnostics.isEmpty()) {
                assertEquals("cannot find symbol\\n  symbol:   variable y\\n  location: class",
                        diagnostics.getFirst());
            }
            assertEquals(1, diagnostics.size());
        }

        assertEquals(1, evaluatedSnippets.size());
    }

    @Test
    void shouldShowBadOperandError() throws Exception {
        CodeRunner codeRunner = new CodeRunner();

        String codeSnippet = OBJECT_MAPPER.writeValueAsString(new CodeRequest("""
                 String name = "dummy";
                 name++;
                """));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(codeSnippet);

        APIGatewayProxyResponseEvent response =
                codeRunner.handleRequest(event, mock(Context.class));

        JShellOutput jShellOutput = OBJECT_MAPPER.readValue(response.getBody(), JShellOutput.class);

        List<EvaluatedSnippet> evaluatedSnippets = jShellOutput.events();

        if (evaluatedSnippets.size() > 1) {
            List<String> diagnostics = evaluatedSnippets.get(1).diagnostics();
            if (!diagnostics.isEmpty()) {
                assertEquals("bad operand type java.lang.String for unary operator '++'",
                        diagnostics.getFirst());
            }
            assertEquals(1, diagnostics.size());
        }

        assertEquals(2, evaluatedSnippets.size());
    }

}
