package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;

import java.awt.Color;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    }

    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        String content = event.getMessage().getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            return;
        }
    }

    private static Optional<String> extractCode(CharSequence fullMessage) {
        Matcher codeBlockMatcher = CODE_BLOCK_EXTRACTOR_PATTERN.matcher(fullMessage);
        if (!codeBlockMatcher.find()) {
            return Optional.empty();
        }
        return Optional.of(codeBlockMatcher.group(1));
    }
}
