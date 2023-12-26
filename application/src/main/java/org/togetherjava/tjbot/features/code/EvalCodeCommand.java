package org.togetherjava.tjbot.features.code;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.features.jshell.JShellEval;
import org.togetherjava.tjbot.features.utils.CodeFence;
import org.togetherjava.tjbot.features.utils.Colors;
import org.togetherjava.tjbot.features.utils.ConnectionFailedException;
import org.togetherjava.tjbot.features.utils.RequestFailedException;

/**
 * Evaluates the given code with jshell.
 * <p>
 * It will not work of the code isn't valid java or jshell compatible code.
 */
final class EvalCodeCommand implements CodeAction {
    private final JShellEval jshellEval;

    EvalCodeCommand(JShellEval jshellEval) {
        this.jshellEval = jshellEval;
    }

    @Override
    public String getLabel() {
        return "Run code";
    }

    @Override
    public MessageEmbed apply(CodeFence codeFence) {
        if (codeFence.code().isEmpty()) {
            return new EmbedBuilder().setColor(Colors.ERROR_COLOR)
                .setDescription("There is nothing to evaluate")
                .build();
        }
        try {
            return jshellEval.evaluateAndRespond(null, codeFence.code(), false, false);
        } catch (RequestFailedException | ConnectionFailedException e) {
            return new EmbedBuilder().setColor(Colors.ERROR_COLOR)
                .setDescription("Request failed: " + e.getMessage())
                .build();
        }
    }

}
