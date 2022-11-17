package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.commands.utils.CodeFence;
import org.togetherjava.tjbot.imc.CompilationResult;
import org.togetherjava.tjbot.imc.InMemoryCompiler;
import org.togetherjava.tjbot.imc.JavacOption;
import org.togetherjava.tjbot.javap.Javap;
import org.togetherjava.tjbot.javap.JavapOption;

import javax.annotation.Nullable;

import java.util.StringJoiner;

// TODO Javadoc
final class BytecodeAction implements CodeAction {
    @Override
    public String getLabel() {
        return "Bytecode";
    }

    @Override
    public MessageEmbed apply(CodeFence codeFence) {
        if (!isLanguageSupported(codeFence.language())) {
            return createResponse("Sorry, only Java is supported.");
        }

        String result = compile(codeFence.code());
        return createResponse(result);
    }

    private static boolean isLanguageSupported(@Nullable String language) {
        // Assume that absence indicates Java code
        return language == null || "java".equals(language);
    }

    private static MessageEmbed createResponse(String content) {
        // Rust highlighting looks decent for bytecode
        CodeFence resultCodeFence = new CodeFence("rust", content);

        return new EmbedBuilder().setTitle("Bytecode")
            .setDescription(resultCodeFence.toMarkdown())
            .setColor(CodeMessageHandler.AMBIENT_COLOR)
            .build();
    }

    private static String compile(String code) {
        // Raw compilation
        CompilationResult compilationResult;
        try {
            compilationResult = InMemoryCompiler.compile(code, JavacOption.DEBUG_ALL);
        } catch (RuntimeException e) {
            // TODO logging
            return "A fatal error has occurred during compilation: " + e.getMessage();
        }

        if (!compilationResult.success()) {
            StringJoiner failureMessage = new StringJoiner("\n", "Compilation failed.\n", "");
            compilationResult.compileInfos()
                .forEach(info -> failureMessage.add(info.diagnostic().toString()));
            return failureMessage.toString();
        }

        // Disassembling
        String disassembledResult;
        try {
            disassembledResult = Javap.disassemble(compilationResult.bytes(), JavapOption.VERBOSE);
        } catch (RuntimeException e) {
            // TODO logging
            return "A fatal error has occurred during disassembly: " + e.getMessage();
        }

        // TODO max length of embeds
        return disassembledResult;
    }
}
