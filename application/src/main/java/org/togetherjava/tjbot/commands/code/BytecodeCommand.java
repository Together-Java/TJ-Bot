package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

// FIXME Dont commit me, im just for testing
public final class BytecodeCommand implements CodeAction {
    @Override
    public String getLabel() {
        return "Show Bytecode";
    }

    @Override
    public MessageEmbed apply(String code) {
        return new EmbedBuilder().setTitle("Bytecode of the code")
            .setDescription("Not implemented yet")
            .setColor(CodeMessageHandler.AMBIENT_COLOR)
            .build();
    }
}
