package org.togetherjava.tjbot.features.jshell.renderer;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellEvalAbortionCause;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.SnippetStatus;

class RendererUtils {
    private RendererUtils() {
    }

    static String abortionCauseToString(JShellEvalAbortionCause abortionCause) {
        if (abortionCause instanceof JShellEvalAbortionCause.TimeoutAbortionCause) {
            return "Allowed time exceeded.";
        } else if (abortionCause instanceof JShellEvalAbortionCause.UnhandledExceptionAbortionCause c) {
            return "Uncaught exception:\n" + c.exceptionClass() + ":" + c.exceptionMessage();
        } else if (abortionCause instanceof JShellEvalAbortionCause.CompileTimeErrorAbortionCause c) {
            return "The code doesn't compile:\n" + String.join("\n", c.errors());
        } else if (abortionCause instanceof JShellEvalAbortionCause.SyntaxErrorAbortionCause) {
            return "The code doesn't compile, there are syntax errors in this code.";
        }
        throw new AssertionError();
    }

    enum GeneralStatus {
        SUCCESS, PARTIAL_SUCCESS, ERROR
    }
    static GeneralStatus getGeneralStatus(JShellResult result) {
        if (result.snippetsResults().isEmpty() && result.abortion() == null) return GeneralStatus.SUCCESS;   // Empty = success
        if (result.snippetsResults().isEmpty())
            return GeneralStatus.ERROR;                                  // Only abortion = failure, special case for syntax error
        if (result.snippetsResults().size() == 1 && result.abortion() != null                        // Only abortion = failure, case for all except syntax error
                && !(result.abortion().cause() instanceof JShellEvalAbortionCause.SyntaxErrorAbortionCause))
            return GeneralStatus.ERROR;

        if (result.abortion() != null) return GeneralStatus.PARTIAL_SUCCESS; // At least one snippet is a success

        return getGeneralStatus(result.snippetsResults().get(result.snippetsResults().size() - 1).status());
    }

    private static GeneralStatus getGeneralStatus(SnippetStatus status) {
        return switch (status) {
            case VALID -> GeneralStatus.SUCCESS;
            case RECOVERABLE_DEFINED, RECOVERABLE_NOT_DEFINED -> GeneralStatus.PARTIAL_SUCCESS;
            case REJECTED -> GeneralStatus.ERROR;
        };
    }
}
