package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;

import javax.annotation.Nullable;

/**
 * Allows to render JShell results.
 */
public class ResultRenderer {
    private final ResultEmbedRenderer2 embedRenderer = new ResultEmbedRenderer2();//TODO
    private final ResultFileRenderer fileRenderer = new ResultFileRenderer();

    /**
     * Renders a JShell result.
     * 
     * @param originator the user from who to display snippet ownership, won't be displayed if null
     * @param showCode if the original should be displayed
     * @param result the JShell result
     * @return the result
     */
    public RenderResult render(@Nullable Member originator, boolean showCode, JShellResult result) {
        if(true/*embedRenderer.canBeSentAsEmbed(originator, showCode, result) TODO*/) {
            return new RenderResult.EmbedResult(embedRenderer.renderToEmbed(originator, showCode, result));
        } else {
            return new RenderResult.FileResult(fileRenderer.renderToFileString(originator, showCode, result));
        }
    }

}
