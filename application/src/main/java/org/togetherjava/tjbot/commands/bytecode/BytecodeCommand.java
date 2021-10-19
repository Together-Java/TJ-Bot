package org.togetherjava.tjbot.commands.bytecode;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.imc.CompilationResult;
import org.togetherjava.tjbot.imc.CompileInfo;
import org.togetherjava.tjbot.imc.JavacOption;
import org.togetherjava.tjbot.imc.IMCompiler;
import org.togetherjava.tjbot.javap.JavapOption;
import org.togetherjava.tjbot.javap.Javap;

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
public final class BytecodeCommand implements EventListener {
    private final Pattern codeBlockExtractorPattern =
            Pattern.compile("```(?:java)?\\s*([\\w\\W]+)```|``?([\\w\\W]+)``?");
    private final Map<Long, List<Long>> replyMap = new HashMap<>();
    private final String commandPrefix = "!bytecode ";
    private final String codeBlockRight = "\n```";
    private final String codeBlockLeft = "```\n";

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (gevent instanceof GuildMessageReceivedEvent event && !event.getAuthor().isBot()) {
            Message message = event.getMessage();
            String content = message.getContentRaw();

            if (!content.startsWith(commandPrefix)) {
                return;
            }

            message.reply("Compiling...")
                .mentionRepliedUser(false)
                .queue(compReply -> compile(message, compReply, parseCommandFromMessage(content)));
        } else if (gevent instanceof GuildMessageDeleteEvent event
                && replyMap.containsKey(event.getMessageIdLong())) {
            deleteMyMessages(event.getMessageIdLong(), event.getChannel());
        } else if (gevent instanceof GuildMessageUpdateEvent event
                && replyMap.containsKey(event.getMessageIdLong())) {
            Message message = event.getMessage();
            long messageIdLong = event.getMessageIdLong();
            TextChannel textChannel = message.getTextChannel();
            List<Long> myMessages = replyMap.get(messageIdLong);

            textChannel.retrieveMessageById(myMessages.get(0)).queue(myMessage -> {
                String content = message.getContentRaw();

                if (!content.startsWith(commandPrefix)) {
                    deleteMyMessages(message.getIdLong(), textChannel);

                    return;
                }

                myMessages.stream()
                    .skip(1)
                    .forEach(id -> event.getChannel()
                        .retrieveMessageById(id)
                        .queue(msg -> msg.delete().queue()));

                myMessage.editMessage("Recompiling...").queue();

                compile(message, myMessage, parseCommandFromMessage(content));
            });
        }
    }

    private void deleteMyMessages(Long msgId, TextChannel channel) {
        replyMap.get(msgId)
            .forEach(id -> channel.retrieveMessageById(id).queue(msg -> msg.delete().queue()));

        replyMap.remove(msgId);
    }

    private void compile(Message userMessage, Message myMessage, String content) {
        replyMap.put(userMessage.getIdLong(), List.of(myMessage.getIdLong()));

        CompilationResult result;

        try {
            result = IMCompiler.getInstance().compile(content, JavacOption.DEBUG_ALL);
        } catch (RuntimeException rex) {
            myMessage
                .editMessage("A fatal error has occurred during compilation. %s"
                    .formatted(rex.toString()))
                .mentionRepliedUser(false)
                .queue();

            return;
        }

        if (!result.success()) {
            myMessage
                .editMessage("Compilation failed." + codeBlockLeft
                        + makeCollection(result.compileInfos()).stream()
                            .map(CompileInfo::diagnostic)
                            .map(Diagnostic::toString)
                            .collect(Collectors.joining("\n"))
                        + codeBlockRight)
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
                } catch (RuntimeException rex) {
                    myMessage
                        .editMessage("A fatal error has occurred during disassembly. %s"
                            .formatted(rex.toString()))
                        .mentionRepliedUser(false)
                        .queue();

                    return;
                }

                String msgResult = surroundInCodeBlock(disassembled);

                disReply.delete().queue();

                if (msgResult.length() <= 2000) {
                    userMessage.reply(msgResult)
                        .mentionRepliedUser(false)
                        .queue(msg -> replyMap.put(userMessage.getIdLong(),
                                List.of(msg.getIdLong())));

                    return;
                }

                List<String> msgResults =
                        takeApart(disassembled, 2000 - surroundInCodeBlock("").length());
                Iterator<String> iterator = msgResults.iterator();
                List<Long> messageIds = new ArrayList<>();

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

                replyMap.put(userMessage.getIdLong(), messageIds);
            });
    }

    private String surroundInCodeBlock(String s) {
        return codeBlockLeft + s + codeBlockRight;
    }

    private List<String> takeApart(String s, int n) {
        List<String> result = new ArrayList<>();
        List<String> lines = Arrays.asList(s.split("\n"));
        int currentLength = 0;
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            if (currentLength + line.length() > n) {
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

    private <E> Collection<E> makeCollection(Iterable<E> iter) {
        Collection<E> list = new ArrayList<>();

        iter.forEach(list::add);

        return list;
    }

    private String parseCommandFromMessage(String messageContent) {
        String withoutPrefix = messageContent.substring(commandPrefix.length());
        Matcher codeBlockMatcher = codeBlockExtractorPattern.matcher(withoutPrefix);

        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1);
        }

        return withoutPrefix;
    }
}
