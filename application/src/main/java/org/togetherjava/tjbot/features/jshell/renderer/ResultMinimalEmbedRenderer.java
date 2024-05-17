package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;

import javax.annotation.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Allows to render JShell results in a minimal way.
 */
class ResultMinimalEmbedRenderer {

    /**
     * Renders a JShell result to an embed, empty if too big.
     * 
     * @param originator the user from who to display snippet ownership, won't be displayed if null
     * @param result the JShell result
     * @return the embed, or empty if too big
     */
    public Optional<MessageEmbed> renderToEmbed(@Nullable Member originator, JShellResult result) {
        List<String> builder = new ArrayList<>();

        setResultToEmbed(builder, result);

        String author = originator == null ? null : originator.getEffectiveName() + "'s result";
        Color color = RendererUtils.getStatusColor(result);

        return RendererUtils.generateEmbed(builder)
            .map(e -> RendererUtils.setMetadataAndBuild(e, author, color));
    }

    private void setResultToEmbed(List<String> builder, JShellResult result) {
        if (result.abortion() != null) {
            builder.add("""
                    ## [WARNING] The code couldn't end properly
                    Cause:
                    %s
                    """.formatted(RendererUtils.abortionCauseToString(result.abortion().cause())));
        }
        builder.add(RendererUtils.stdoutToMarkdownString(result));
    }
}
