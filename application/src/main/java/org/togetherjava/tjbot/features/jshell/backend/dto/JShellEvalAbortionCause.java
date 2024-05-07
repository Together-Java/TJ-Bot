package org.togetherjava.tjbot.features.jshell.backend.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

/**
 * The cause of an abortion, see the implementations of this sealed interface for the possible
 * causes.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public sealed interface JShellEvalAbortionCause {

    /**
     * The timeout was exceeded.
     */
    @JsonTypeName("TIMEOUT")
    record TimeoutAbortionCause() implements JShellEvalAbortionCause {
    }

    /**
     * An exception was thrown but never caught.
     *
     * @param exceptionClass the class of the exception
     * @param exceptionMessage the message of the exception
     */
    @JsonTypeName("UNCAUGHT_EXCEPTION")
    record UnhandledExceptionAbortionCause(String exceptionClass,
            String exceptionMessage) implements JShellEvalAbortionCause {
    }

    /**
     * The code doesn't compile, but at least the syntax is correct.
     * 
     * @param errors the compilation errors
     */
    @JsonTypeName("COMPILE_TIME_ERROR")
    record CompileTimeErrorAbortionCause(List<String> errors) implements JShellEvalAbortionCause {
    }

    /**
     * The code doesn't compile, and the syntax itself isn't correct.
     */
    @JsonTypeName("SYNTAX_ERROR")
    record SyntaxErrorAbortionCause() implements JShellEvalAbortionCause {
    }
}
