package org.togetherjava.tjbot.features.code;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.features.chatgpt.ChatGptService;
import org.togetherjava.tjbot.features.utils.CodeFence;
import org.togetherjava.tjbot.formatter.Formatter;

/**
 * Formats the given code.
 * <p>
 * While it will attempt formatting for any language, best results are achieved for Java code.
 */
final class FormatCodeCommand implements CodeAction {
    private final Formatter formatter = new Formatter();
    private final ChatGPTFormatter chatGPTFormatter;

    /**
     * Initializes a {@link FormatCodeCommand} which formats code.
     *
     * @param service the {@link ChatGptService} instance to be used for code formatting
     */
    public FormatCodeCommand(ChatGptService service) {
        this.chatGPTFormatter = new ChatGPTFormatter(service);
    }

    @Override
    public String getLabel() {
        return "Format";
    }

    @Override
    public MessageEmbed apply(CodeFence codeFence) {
        String formattedCode = formatCode(codeFence.code());
        // Any syntax highlighting is better than none
        String language = codeFence.language() == null ? "java" : codeFence.language();

        CodeFence formattedCodeFence = new CodeFence(language, formattedCode);

        return new EmbedBuilder().setTitle("Formatted code")
            .setDescription(formattedCodeFence.toMarkdown())
            .setColor(CodeMessageHandler.AMBIENT_COLOR)
            .build();
    }

    /**
     * Formats the provided code using either the ChatGPT formatter or the generic formatter.
     *
     * @param code the code to be formatted
     * @return the formatted code
     */
    private String formatCode(CharSequence code) {
        return chatGPTFormatter.format(code).orElse(formatter.format(code));
    }
}
