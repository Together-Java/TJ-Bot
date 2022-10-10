package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import org.togetherjava.tjbot.commands.BotCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.MessageContextCommand;
import org.togetherjava.tjbot.formatter.Formatter;
import org.togetherjava.tjbot.formatter.tokenizer.Lexer;

import java.util.Optional;

// TODO Doc
public final class FormatCodeCommand extends BotCommandAdapter implements MessageContextCommand {
    private final Lexer lexer;
    private final Formatter formatter;

    // TODO doc
    public FormatCodeCommand() {
        super(Commands.message("format-code"), CommandVisibility.GUILD);

        lexer = new Lexer();
        formatter = new Formatter();
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        String content = event.getTarget().getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            event.reply("Could not find code in the message.").setEphemeral(true).queue();
            return;
        }

        String formattedCode = formatCode(maybeCode.orElseThrow());
        MessageEmbed response = new EmbedBuilder().setTitle("Formatted code")
            .setDescription(formattedCode)
            .setColor(AMBIENT_COLOR)
            .build();

        event.replyEmbeds(response).queue();
    }

    private String formatCode(String code) {
        return formatter.format(code, lexer);
    }
}
