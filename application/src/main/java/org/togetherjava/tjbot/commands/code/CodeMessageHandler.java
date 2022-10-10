package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;

import java.awt.Color;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO Also needs UserInteractor / BotCommandAdapter (can one class even implement both wrt
// prefixes? maybe split into two)
public final class CodeMessageHandler extends MessageReceiverAdapter {
    private static final Color AMBIENT_COLOR = Color.decode("#FDFD96");
    // TODO doc, lol
    private static final Pattern CODE_BLOCK_EXTRACTOR_PATTERN =
            Pattern.compile("```(?:java)?\\s*([\\w\\W]+)```|``?([\\w\\W]+)``?");

    public CodeMessageHandler() {
        super(Pattern.compile(".*"));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            return;
        }


        event.getMessage().reply(createCodeActionsResponse()).queue();
    }

    private static MessageCreateData createCodeActionsResponse() {
        // TODO ...
        return new MessageCreateBuilder().setContent("Detected code, here are some useful tools:")
            .setActionRow(Button.primary("some id", "Format"))
            .build();
    }

    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        String content = event.getMessage().getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            return;
        }

        // TODO ...
    }

    @Override
    public void onMessageDeleted(MessageDeleteEvent event) {
        // TODO ...
    }

    private static Optional<String> extractCode(CharSequence fullMessage) {
        Matcher codeBlockMatcher = CODE_BLOCK_EXTRACTOR_PATTERN.matcher(fullMessage);
        if (!codeBlockMatcher.find()) {
            return Optional.empty();
        }
        return Optional.of(codeBlockMatcher.group(1));
    }
}
