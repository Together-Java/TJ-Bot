package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellEvalAbortion;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellSnippetResult;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;
import java.awt.*;

import static org.togetherjava.tjbot.features.utils.Colors.*;

/**
 * Allows to render JShell results.
 */
class ResultEmbedRenderer {

    /**
     * Renders a JShell result to an embed.
     * 
     * @param originator the user from who to display snippet ownership, won't be displayed if null
     * @param showCode if the original should be displayed
     * @param result the JShell result
     * @return the ember builder
     */
    public MessageEmbed renderToEmbed(@Nullable User originator, boolean showCode, JShellResult result) {
        EmbedBuilder builder = new EmbedBuilder();
        if (originator != null) {
            builder.setAuthor(originator.getName() + "'s result");
        }
        builder.setColor(getStatusColor(result));

        setResultToEmbed(showCode, builder, result);

        return builder.build();
    }
    boolean canBeSentAsEmbed(@Nullable User originator, boolean showCode, JShellResult result) {
        if(showCode) {
            int descLength = "```java\n".length();
            for (JShellSnippetResult r : result.snippetsResults()) {
                descLength += """
                        // Snippet %d, %s
                        %s
                        jshell> %s
                        """.formatted(r.id(), r.status(), r.source(), r.result() == null ? "" : r.result()).length();
            }
            descLength += "```".length();
            if(descLength > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                return false;
            }
            int abortionLength = 0;
            if(result.abortion() != null) {
                abortionLength += abortionToString(result.abortion()).length();
                if(abortionLength > MessageEmbed.VALUE_MAX_LENGTH) {
                    return false;
                }
                abortionLength += "[WARNING] The code couldn't end properly...".length();
            }
            int resultLength = getResultLength(result);
            return authorLength(originator) + descLength + abortionLength + resultLength <= MessageEmbed.EMBED_MAX_LENGTH_BOT;
        } else {
            int abortionLength = 0;
            if(result.abortion() != null) {
                abortionLength += ("[WARNING] The code couldn't end properly...\n" + abortionToString(result.abortion())).length();
                if(abortionLength > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                    return false;
                }
            }
            int resultLength = getResultLength(result);
            return authorLength(originator) + abortionLength + resultLength <= MessageEmbed.EMBED_MAX_LENGTH_BOT;
        }
    }
    private int getResultLength(JShellResult result) {
        return result.stdout().isEmpty()
                ? "No result".length()
                : ("Result" + getSdtOut(result)).length();
    }
    private int authorLength(@Nullable User originator) {
        return originator == null ? 0 : (originator.getName() + "'s result").length();
    }

    private void setResultToEmbed(boolean showCode, EmbedBuilder builder, JShellResult result) {
        if (showCode) {
            StringBuilder sb = new StringBuilder("```java\n");
            for (JShellSnippetResult r : result.snippetsResults()) {
                sb.append("""
                        // Snippet %d, %s
                        %s
                        jshell> %s
                        """.formatted(r.id(), r.status(), r.source(), r.result() == null ? "" : r.result()));
            }
            sb.append("```");
            builder.setDescription(sb.toString());
            if(result.abortion() != null) {
                builder.addField("[WARNING] The code couldn't end properly...", abortionToString(result.abortion()), false);
            }
        } else {
            if(result.abortion() != null) {
                builder.setDescription("[WARNING] The code couldn't end properly...\n" + abortionToString(result.abortion()));
            }
        }
        setStdoutToEmbed(builder, result);
    }
    private void setStdoutToEmbed(EmbedBuilder builder, JShellResult result) {
        if(result.stdout().isEmpty()) {
            builder.addField("No result", "", false);
        } else {
            builder.addField("Result", getSdtOut(result), false);
        }
    }

    private String getSdtOut(JShellResult result) {
        String stdout = result.stdout();
        if(result.stdoutOverflow()) {
            stdout += MessageUtils.ABBREVIATION;
        }
        return MessageUtils.abbreviate(stdout, MessageEmbed.VALUE_MAX_LENGTH);
    }

    private String abortionToString(JShellEvalAbortion abortion) {
        String s = """
            Problematic source code:
            ```java
            %s
            ```
            Cause:
            %s
            
            """.formatted(abortion.sourceCause(), RendererUtils.abortionCauseToString(abortion.cause()));
        if(!abortion.remainingSource().isEmpty()) {
            s += """
                 Remaining code:
                ```java
                %s```""".formatted( abortion.remainingSource());
        }
        return s;
    }

    private Color getStatusColor(JShellResult result) {
        return switch (RendererUtils.getGeneralStatus(result)) {
            case SUCCESS -> SUCCESS_COLOR;
            case PARTIAL_SUCCESS -> PARTIAL_SUCCESS_COLOR;
            case ERROR -> ERROR_COLOR;
        };
    }
}
