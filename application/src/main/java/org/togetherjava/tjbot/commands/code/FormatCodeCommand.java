package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.commands.utils.CodeFence;
import org.togetherjava.tjbot.formatter.Formatter;
import org.togetherjava.tjbot.formatter.tokenizer.Lexer;

/**
 * Formats the given code.
 * <p>
 * While it will attempt formatting for any language, best results are achieved for Java code.
 */
final class FormatCodeCommand implements CodeAction {
    private final Lexer lexer;
    private final Formatter formatter;

    FormatCodeCommand() {
        lexer = new Lexer();
        formatter = new Formatter();
    }

    @Override
    public String getLabel() {
        return "Format";
    }

    @Override
    public MessageEmbed apply(CodeFence codeFence) {
        String formattedCode = formatCode(codeFence.code());
        CodeFence formattedCodeFence = new CodeFence(codeFence.language(), formattedCode);

        return new EmbedBuilder().setTitle("Formatted code")
            .setDescription(formattedCodeFence.toMarkdown())
            .setColor(CodeMessageHandler.AMBIENT_COLOR)
            .build();
    }

    private String formatCode(String code) {
        return formatter.format(code, lexer);
    }
}