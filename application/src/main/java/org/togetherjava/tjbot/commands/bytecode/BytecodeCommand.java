package org.togetherjava.tjbot.commands.bytecode;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.imc.CompilationResult;
import org.togetherjava.tjbot.imc.CompileInfo;
import org.togetherjava.tjbot.imc.IMCompiler;
import org.togetherjava.tjbot.imc.JavacOption;
import org.togetherjava.tjbot.javap.Javap;
import org.togetherjava.tjbot.javap.JavapOption;

import javax.tools.Diagnostic;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bytecode command that uses the standard-text commands (!bytecode ...) because multiline support
 * for slashcommands is soon<sup>tm</sup>.<br>
 * Example usage:
 * <p>
 *
 * <pre>
 * {@code
 *     !bytecode ```java
 *     class Test {
 *         public void foo() {
 *             System.out.println("Hello World!");
 *         }
 *     }
 *     ```
 * }
 * </pre>
 */
public final class BytecodeCommand extends ListenerAdapter {
    private static final String COMMAND_PREFIX = "!bytecode ";
    private static final String CODE_BLOCK_OPENING = "```\n";
    private static final String CODE_BLOCK_CLOSING = "\n```";
    /**
     * Discord's message size limit (in characters).
     *
     * @see <a href="https://discord.com/developers/docs/resources/channel#create-message">discord.com/developers</a>
     */
    private static final int DISCORD_MESSAGE_LENGTH = 2000;

    private final Pattern codeBlockExtractorPattern =
            Pattern.compile("```(?:java)?\\s*([\\w\\W]+)```|``?([\\w\\W]+)``?");
    private final Map<Long, List<Long>> userMessageToMyMessages = new HashMap<>();

    // Fresh compile when a user sends a message with !bytecode ...
    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw();

        if (!content.startsWith(COMMAND_PREFIX)) {
            return;
        }

        message.reply("Compiling...")
                .mentionRepliedUser(false)
                .queue(compReply -> compile(message, compReply, parseCommandFromMessage(content)));
    }

    // Delete our messages if the user deletes their request message
    @Override
    public void onGuildMessageDelete(@NotNull GuildMessageDeleteEvent event) {
        long mesageIdLong = event.getMessageIdLong();

        if (userMessageToMyMessages.containsKey(mesageIdLong)) {
            deleteMyMessages(mesageIdLong, event.getChannel());
        }
    }

    // Recompile when the user sends edits their request message
    @Override
    public void onGuildMessageUpdate(@NotNull GuildMessageUpdateEvent event) {
        Message message = event.getMessage();
        long messageIdLong = event.getMessageIdLong();

        if (!userMessageToMyMessages.containsKey(messageIdLong)) {
            return;
        }

        TextChannel textChannel = message.getTextChannel();
        List<Long> myMessages = userMessageToMyMessages.get(messageIdLong);

        if (myMessages.size() == 0) {
            message.reply(
                            "An unknown error occurred (`userMessagesToMyMessages.get(messageIdLong).size() == 0`)")
                    .queue();

            return;
        }

        textChannel.retrieveMessageById(myMessages.get(0)).queue(myMessage -> {
            String content = message.getContentRaw();

            if (!content.startsWith(COMMAND_PREFIX)) {
                deleteMyMessages(message.getIdLong(), textChannel);

                return;
            }

            textChannel.purgeMessagesById(myMessages.stream()
                    .skip(1) // skip our first message to edit it
                    .mapToLong(l -> l)
                    .toArray());

            myMessage.editMessage("Recompiling...").queue();

            compile(message, myMessage, parseCommandFromMessage(content));
        });
    }

    private void deleteMyMessages(@NotNull Long msgId, @NotNull TextChannel channel) {
        if (!userMessageToMyMessages.containsKey(msgId)) {
            return;
        }

        channel.purgeMessagesById(userMessageToMyMessages.get(msgId)
            .stream()
            .map(String::valueOf)
            .collect(Collectors.toList()));

        userMessageToMyMessages.remove(msgId);
    }

    private void compile(@NotNull Message userMessage, @NotNull Message myMessage,
            @NotNull String content) {
        userMessageToMyMessages.put(userMessage.getIdLong(), List.of(myMessage.getIdLong()));

        CompilationResult result;

        try {
            result = IMCompiler.getInstance().compile(content, JavacOption.DEBUG_ALL);
        } catch (RuntimeException e) {
            myMessage
                .editMessage("A fatal error has occurred during compilation. %s"
                    .formatted(e.getMessage()))
                .mentionRepliedUser(false)
                .queue();

            return;
        }

        if (!result.success()) {
            myMessage
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

        myMessage.editMessage("Compilation was successfull! Disassembling...")
            .mentionRepliedUser(false)
            .queue(disReply -> {
                String disassembled;

                try {
                    disassembled =
                            Javap.getInstance().disassemble(result.bytes(), JavapOption.VERBOSE);
                } catch (RuntimeException e) {
                    myMessage
                        .editMessage("A fatal error has occurred during disassembly. %s"
                            .formatted(e.getMessage()))
                        .mentionRepliedUser(false)
                        .queue();

                    return;
                }

                String msgResult = surroundInCodeBlock(disassembled);

                disReply.delete().queue();

                if (msgResult.length() <= DISCORD_MESSAGE_LENGTH) {
                    userMessage.reply(msgResult)
                        .mentionRepliedUser(false)
                        .queue(msg -> userMessageToMyMessages.put(userMessage.getIdLong(),
                                List.of(msg.getIdLong())));

                    return;
                }

                List<String> msgResults = takeApart(disassembled,
                        DISCORD_MESSAGE_LENGTH - surroundInCodeBlock("").length());
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

                userMessageToMyMessages.put(userMessage.getIdLong(), messageIds);
            });
    }

    private String surroundInCodeBlock(@NotNull String s) {
        return CODE_BLOCK_OPENING + s + CODE_BLOCK_CLOSING;
    }

    private List<String> takeApart(@NotNull String message, int maxPartLength) {
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
        String withoutPrefix = messageContent.substring(COMMAND_PREFIX.length());
        Matcher codeBlockMatcher = codeBlockExtractorPattern.matcher(withoutPrefix);

        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1);
        }

        return withoutPrefix;
    }
}
