package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellEvalAbortion;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellSnippetResult;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

class ResultFileRenderer {
    private static final String SUCCESS = "SUCCESS!";
    private static final String PARTIAL_SUCCESS = "PARTIAL SUCCESS -_-";
    private static final String ERROR = "ERROR... :(!";

    /**
     * Renders a JShell result to a file.
     *
     * @param originator the user from who to display snippet ownership, won't be displayed if null
     * @param showCode   if the original should be displayed
     * @param result     the JShell result
     * @return the content
     */
    public String renderToFileString(@Nullable Member originator, boolean showCode, JShellResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("// ").append(getGeneralStatus(result)).append("\n");

        if (originator != null) {
            builder.append("// ").append(originator.getEffectiveName()).append("'s result\n\n");
        }

        setResultToBuilder(showCode, builder, result);

        return builder.toString();
    }

    private void setResultToBuilder(boolean showCode, StringBuilder builder, JShellResult result) {
        if (showCode) {
            for (JShellSnippetResult r : result.snippetsResults()) {
                builder.append("""
                        // Snippet %d, %s
                        %s
                        jshell> %s
                        """.formatted(r.id(), r.status(), r.source(), r.result() == null ? "" : r.result()));
            }
        }
        if (result.abortion() != null) {
            builder.append('\n');
            builder.append("// [WARNING] The code couldn't end properly...\n");
            builder.append(abortionToString(result.abortion()));
        }
        setStdoutToBuilder(builder, result);
    }

    private void setStdoutToBuilder(StringBuilder builder, JShellResult result) {
        if (result.stdout().isEmpty()) {
            builder.append("// No result");
        } else {
            builder.append("// Result:\n");
            builder.append(getSdtOut(result));
        }
    }

    private String getSdtOut(JShellResult result) {
        String stdout = result.stdout();
        stdout = "// " + stdout.replace("\n", "\n// ");
        if (result.stdoutOverflow()) {
            stdout += MessageUtils.ABBREVIATION;
        }
        return stdout;
    }

    private String abortionToString(JShellEvalAbortion abortion) {
        return """
                // Cause:
                // %s
                //
                // Remaining code:
                %s
                """.formatted(RendererUtils.abortionCauseToString(abortion.cause()), abortion.remainingSource());
    }

    private String getGeneralStatus(JShellResult result) {
        return switch (RendererUtils.getGeneralStatus(result)) {
            case SUCCESS -> SUCCESS;
            case PARTIAL_SUCCESS -> PARTIAL_SUCCESS;
            case ERROR -> ERROR;
        };
    }
}
