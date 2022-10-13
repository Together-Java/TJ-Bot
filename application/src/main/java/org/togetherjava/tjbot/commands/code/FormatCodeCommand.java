package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.formatter.Formatter;
import org.togetherjava.tjbot.formatter.tokenizer.Lexer;

// TODO Doc
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
    public MessageEmbed apply(String code) {
        String formattedCode = formatCode(code);

        return new EmbedBuilder().setTitle("Formatted code")
            .setDescription(formattedCode)
            .setColor(CodeMessageHandler.AMBIENT_COLOR)
            .build();
    }

    private String formatCode(String code) {
        return formatter.format(code, lexer);
    }
}
