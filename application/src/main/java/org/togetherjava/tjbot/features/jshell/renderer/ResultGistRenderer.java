package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.kohsuke.github.GHGist;
import org.kohsuke.github.GHGistBuilder;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellEvalAbortion;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellSnippetResult;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.Optional;

class ResultGistRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ResultGistRenderer.class);
    private static final String SUCCESS = "SUCCESS!";
    private static final String PARTIAL_SUCCESS = "PARTIAL SUCCESS -_-";
    private static final String ERROR = "ERROR... :(!";

    /**
     * Renders a JShell result to a gist.
     *
     * @param gistApiToken the token to use to send the result to gist
     * @param originator the user from who to display snippet ownership, won't be displayed if null
     * @param showCode if the original should be displayed
     * @param result the JShell result
     * @return the content
     */
    public Optional<MessageEmbed> renderToGist(String gistApiToken, @Nullable Member originator,
            boolean showCode, JShellResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("// ").append(getGeneralStatus(result)).append("\n");

        if (originator != null) {
            builder.append("// ").append(originator.getEffectiveName()).append("'s result\n\n");
        }

        setResultToBuilder(showCode, builder, result);

        String text = builder.toString();

        GHGist gist;
        try {
            GHGistBuilder gistBuilder = new GitHubBuilder().withOAuthToken(gistApiToken)
                .build()
                .createGist()
                .public_(false);
            if (originator != null) {
                gistBuilder.description("Uploaded by " + originator.getEffectiveName());
            }
            gistBuilder.file((originator == null ? "JShell" : originator.getEffectiveName())
                    + "'s result.java", text);

            gist = gistBuilder.create();
        } catch (IOException ex) {
            logger.error("Couldn't send JShell result to Gist", ex);
            return Optional.empty();
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle("The result was too big and so was uploaded to Gist: " + gist.getHtmlUrl()
                    + "\n↓This is the output of the JShell execution.↓")
            .setDescription(getSdtOut(result).transform(s -> s.isBlank() ? "Nothing" : s))
            .setColor(RendererUtils.getStatusColor(result));
        if (originator != null) {
            embedBuilder.setAuthor(originator.getEffectiveName() + "'s result");
        }
        return Optional.of(embedBuilder.build());
    }

    private void setResultToBuilder(boolean showCode, StringBuilder builder, JShellResult result) {
        if (showCode) {
            for (JShellSnippetResult r : result.snippetsResults()) {
                builder.append("""
                        // Snippet %d, %s
                        %s
                        jshell> %s
                        """.formatted(r.id(), r.status(), r.source(),
                        r.result() == null ? "" : r.result()));
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
            builder.append(getSdtOutAsComment(result));
        }
    }

    private String getSdtOut(JShellResult result) {
        String stdout = result.stdout().replace("\n", "\n// ");
        if (result.stdoutOverflow()) {
            stdout += MessageUtils.ABBREVIATION;
        }
        return stdout;
    }

    private String getSdtOutAsComment(JShellResult result) {
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
                """.formatted(RendererUtils.abortionCauseToString(abortion.cause()),
                abortion.remainingSource());
    }

    private String getGeneralStatus(JShellResult result) {
        return switch (RendererUtils.getGeneralStatus(result)) {
            case SUCCESS -> SUCCESS;
            case PARTIAL_SUCCESS -> PARTIAL_SUCCESS;
            case ERROR -> ERROR;
        };
    }
}
