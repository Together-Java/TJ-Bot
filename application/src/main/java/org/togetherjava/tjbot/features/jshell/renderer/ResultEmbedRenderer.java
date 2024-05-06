package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellEvalAbortion;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellSnippetResult;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Allows to render JShell results.
 */
class ResultEmbedRenderer {
    private static final int ABORTION_CODE_SIZE_LIMIT = 2000;

    /**
     * Renders a JShell result to an embed, empty if too big.
     * 
     * @param originator the user from who to display snippet ownership, won't be displayed if null
     * @param showCode if the original should be displayed
     * @param result the JShell result
     * @return the embed, or empty if too big
     */
    public Optional<MessageEmbed> renderToEmbed(@Nullable Member originator, boolean showCode,
            JShellResult result) {
        List<String> builder = new ArrayList<>();

        setResultToEmbed(showCode, builder, result);

        String author = originator == null ? null : originator.getEffectiveName() + "'s result";
        Color color = RendererUtils.getStatusColor(result);

        return RendererUtils.generateEmbed(builder)
            .map(e -> RendererUtils.setMetadataAndBuild(e, author, color));
    }

    private void setResultToEmbed(boolean showCode, List<String> builder, JShellResult result) {
        if (showCode) {
            builder.add("## Snippets\n");
            for (JShellSnippetResult r : result.snippetsResults()) {
                builder.add("""
                        ### Snippet %d, %s
                        ```java
                        %s```%s""".formatted(r.id(), r.status(), r.source(),
                        resultToString(r.result())));
            }
        }
        if (result.abortion() != null) {
            builder.add("## [WARNING] The code couldn't end properly...\n"
                    + abortionToString(result.abortion()));
        }
        builder.add(RendererUtils.stdoutToMarkdownString(result));
    }

    private String resultToString(@Nullable String result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        if (!result.contains("\n")) {
            return "\njshell> `" + result + "`";
        } else {
            return "\njshell> ↓```\n" + result + "```";
        }
    }

    private String abortionToString(JShellEvalAbortion abortion) {
        String s = """
                Problematic source code:
                ```java
                %s```
                Cause:
                %s
                """.formatted(
                MessageUtils.abbreviate(abortion.sourceCause(), ABORTION_CODE_SIZE_LIMIT),
                RendererUtils.abortionCauseToString(abortion.cause()));
        if (!abortion.remainingSource().isEmpty()) {
            s += """

                    Remaining code:
                    ```java
                    %s```""".formatted(
                    MessageUtils.abbreviate(abortion.remainingSource(), ABORTION_CODE_SIZE_LIMIT));
        }
        return s;
    }
}
