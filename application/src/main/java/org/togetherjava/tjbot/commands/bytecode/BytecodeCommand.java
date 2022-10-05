package org.togetherjava.tjbot.commands.bytecode;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.togetherjava.tjbot.commands.BotCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.MessageContextCommand;
import org.togetherjava.tjbot.commands.MessageReceiver;
import org.togetherjava.tjbot.imc.CompilationResult;
import org.togetherjava.tjbot.imc.CompileInfo;
import org.togetherjava.tjbot.imc.InMemoryCompiler;
import org.togetherjava.tjbot.imc.JavacOption;
import org.togetherjava.tjbot.javap.Javap;
import org.togetherjava.tjbot.javap.JavapOption;

import javax.tools.Diagnostic;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bytecode command that uses the message context commands Example usage:
 * <p>
 *
 * <pre>
 * {@code
 *     ```java
 *     class Test {
 *         public void foo() {
 *             System.out.println("Hello World!");
 *         }
 *     }
 *     ```
 * }
 * </pre>
 *
 * then apply the application
 */
public final class BytecodeCommand extends BotCommandAdapter
        implements MessageContextCommand, MessageReceiver {
    private static final String CODE_BLOCK_OPENING = "```rust\n";
    private static final String CODE_BLOCK_CLOSING = "\n```";
    private static final Pattern ANY_CHANNEL_NAME = Pattern.compile(".*");

    private final Pattern codeBlockExtractorPattern =
            Pattern.compile("```(?:java)?\\s*([\\w\\W]+)```|``?([\\w\\W]+)``?");
    private final Map<Long, List<Long>> userToBotMessages = new HashMap<>();

    public BytecodeCommand() {
        super(Commands.message("View bytecode"), CommandVisibility.GUILD);
    }

    @Override
    public Pattern getChannelNamePattern() {
        return ANY_CHANNEL_NAME;
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        Message message = event.getTarget();
        String content = message.getContentRaw();

        event.reply("Compiling...")
            .mentionRepliedUser(false)
            .flatMap(InteractionHook::retrieveOriginal)
            .queue(compReply -> compile(message, compReply, parseCommandFromMessage(content)));
    }

    @Override
    public void onMessageDeleted(MessageDeleteEvent event) {
        long mesageIdLong = event.getMessageIdLong();

        if (userToBotMessages.containsKey(mesageIdLong)) {
            deleteMyMessages(mesageIdLong, event.getTextChannel());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Method isn't needed, context-commands are used for the initial message
    }

    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        Message message = event.getMessage();
        long messageIdLong = event.getMessageIdLong();

        if (!userToBotMessages.containsKey(messageIdLong)) {
            return;
        }

        TextChannel textChannel = message.getTextChannel();
        List<Long> botMessages = userToBotMessages.get(messageIdLong);

        if (botMessages.isEmpty()) {
            message.reply("An unknown error occurred").queue();

            return;
        }

        textChannel.retrieveMessageById(botMessages.get(0)).queue(botMessage -> {
            textChannel.purgeMessagesById(botMessages.stream()
                .skip(1) // skip the first bot message to edit it
                .map(String::valueOf)
                .toList());

            botMessage.editMessage("Recompiling...").queue();

            compile(message, botMessage, parseCommandFromMessage(message.getContentRaw()));
        });
    }

    private void deleteMyMessages(Long msgId, TextChannel channel) {
        if (!userToBotMessages.containsKey(msgId)) {
            return;
        }

        channel
            .purgeMessagesById(userToBotMessages.get(msgId).stream().map(String::valueOf).toList());

        userToBotMessages.remove(msgId);
    }

    private void compile(Message userMessage, Message botMessage, String content) {
        userToBotMessages.put(userMessage.getIdLong(), List.of(botMessage.getIdLong()));

        CompilationResult result;

        try {
            result = InMemoryCompiler.compile(content, JavacOption.DEBUG_ALL);
        } catch (RuntimeException e) {
            botMessage
                .editMessage("A fatal error has occurred during compilation. %s"
                    .formatted(e.getMessage()))
                .mentionRepliedUser(false)
                .queue();

            return;
        }

        if (!result.success()) {
            botMessage
                .editMessage("Compilation failed." + CODE_BLOCK_OPENING
                        + iterToCollection(result.compileInfos()).stream()
                            .map(CompileInfo::diagnostic)
                            .map(Diagnostic::toString)
                            .collect(Collectors.joining("\n"))
                        + CODE_BLOCK_CLOSING)
                .mentionRepliedUser(false)
                .queue();

            return;
        }

        botMessage.editMessage("Compilation was successfull! Disassembling...")
            .mentionRepliedUser(false)
            .queue(disReply -> {
                String disassembled;

                try {
                    disassembled = Javap.disassemble(result.bytes(), JavapOption.VERBOSE);
                } catch (RuntimeException e) {
                    botMessage
                        .editMessage("A fatal error has occurred during disassembly. %s"
                            .formatted(e.getMessage()))
                        .mentionRepliedUser(false)
                        .queue();

                    return;
                }

                String msgResult = surroundInCodeBlock(disassembled);

                disReply.delete().queue();

                if (msgResult.length() <= Message.MAX_CONTENT_LENGTH) {
                    userMessage.reply(msgResult)
                        .mentionRepliedUser(false)
                        .queue(msg -> userToBotMessages.put(userMessage.getIdLong(),
                                List.of(msg.getIdLong())));

                    return;
                }

                List<String> msgResults = explodeEvery(disassembled,
                        Message.MAX_CONTENT_LENGTH - surroundInCodeBlock("").length());
                Iterator<String> iterator = msgResults.iterator();
                List<Long> messageIds = new ArrayList<>(msgResults.size());

                if (iterator.hasNext()) {
                    userMessage.reply(surroundInCodeBlock(iterator.next()))
                        .mentionRepliedUser(false)
                        .queue(msg -> messageIds.add(msg.getIdLong()));
                }

                while (iterator.hasNext()) {
                    disReply.getTextChannel()
                        .sendMessage(surroundInCodeBlock(iterator.next()))
                        .queue(msg -> messageIds.add(msg.getIdLong()));
                }

                userToBotMessages.put(userMessage.getIdLong(), messageIds);
            });
    }

    private String surroundInCodeBlock(String s) {
        return CODE_BLOCK_OPENING + s + CODE_BLOCK_CLOSING;
    }

    /**
     * Example:
     *
     * <pre>
     * {@code
     * takeApart("Hello\nWorld!", 3) returns List("Hel", "lo", "Wor", "ld!")
     * }
     * </pre>
     */
    private List<String> explodeEvery(String message, int maxPartLength) {
        List<String> result = new ArrayList<>();
        String[] lines = message.split("\n");
        int currentLength = 0;
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            if (currentLength + line.length() > maxPartLength) {
                result.add(buffer.toString());
                buffer = new StringBuilder();
                currentLength = 0;
            }

            buffer.append(line);
            currentLength += line.length();
        }

        if (!buffer.isEmpty()) {
            result.add(buffer.toString());
        }

        return result;
    }

    private <E> Collection<E> iterToCollection(Iterable<E> iter) {
        Collection<E> list = new ArrayList<>();

        iter.forEach(list::add);

        return list;
    }

    private String parseCommandFromMessage(String messageContent) {
        Matcher codeBlockMatcher = codeBlockExtractorPattern.matcher(messageContent);

        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1);
        }

        return messageContent;
    }
}
