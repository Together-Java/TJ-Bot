package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;

import javax.annotation.Nullable;

import java.awt.Color;

/**
 * Allows to render JShell results.
 */
public class ResultRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ResultRenderer.class);
    private final ResultEmbedRenderer embedRenderer = new ResultEmbedRenderer();
    private final ResultGistRenderer gistRenderer = new ResultGistRenderer();
    private final ResultMinimalEmbedRenderer minimalEmbedRenderer =
            new ResultMinimalEmbedRenderer();

    /**
     * Renders a JShell result.
     *
     * @param gistApiToken the token to use to send the result to gist, in case it is too big for an
     *        embed
     * @param originator the user from who to display snippet ownership, won't be displayed if null
     * @param showCode if the original should be displayed
     * @param result the JShell result
     * @return the result
     */
    public MessageEmbed render(String gistApiToken, @Nullable Member originator, boolean showCode,
            JShellResult result) {
        return embedRenderer.renderToEmbed(originator, showCode, result)
            .or(() -> gistRenderer.renderToGist(gistApiToken, originator, showCode, result))
            .or(() -> minimalEmbedRenderer.renderToEmbed(originator, result))
            .orElseGet(() -> renderFailure(result));
    }

    private MessageEmbed renderFailure(JShellResult result) {
        logger.error("Couldn't render JShell result {} ", result);
        return new EmbedBuilder()
            .setTitle("Couldn't render the result, please contact a moderator.")
            .setColor(Color.RED)
            .build();
    }

}
