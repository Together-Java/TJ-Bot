package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.commands.UserInteractionType;
import org.togetherjava.tjbot.commands.UserInteractor;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.commands.componentids.ComponentIdInteractor;

import java.awt.Color;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeMessageHandler extends MessageReceiverAdapter implements UserInteractor {
    static final Color AMBIENT_COLOR = Color.decode("#FDFD96");
    // TODO doc, lol
    private static final Pattern CODE_BLOCK_EXTRACTOR_PATTERN =
            Pattern.compile("```(?:java)?\\s*([\\w\\W]+)```|``?([\\w\\W]+)``?");

    private final ComponentIdInteractor componentIdInteractor;
    private final FormatCodeCommand formatCodeCommand;

    // TODO Use a cafeeine cache
    private final Map<Long, Long> originalMessageToCodeReply =
            Collections.synchronizedMap(new HashMap<>());

    public CodeMessageHandler() {
        super(Pattern.compile(".*"));

        componentIdInteractor = new ComponentIdInteractor(getInteractionType(), getName());

        formatCodeCommand = new FormatCodeCommand();
    }

    @Override
    public String getName() {
        return "code-actions";
    }

    @Override
    public UserInteractionType getInteractionType() {
        return UserInteractionType.OTHER;
    }

    @Override
    public void onSelectionMenu(SelectMenuInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message originalMessage = event.getMessage();
        String content = originalMessage.getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            return;
        }

        originalMessage.reply(createCodeReplyMessage(originalMessage.getIdLong()))
            .map(replyMessage -> originalMessageToCodeReply.put(originalMessage.getIdLong(),
                    replyMessage.getIdLong()))
            .queue();
    }

    private MessageCreateData createCodeReplyMessage(long originalMessageId) {
        return new MessageCreateBuilder().setContent("Detected code, here are some useful tools:")
            .setActionRow(Button.primary(
                    componentIdInteractor.generateComponentId(Long.toString(originalMessageId)),
                    "Format"))
            .build();
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        long originalMessageId = Long.parseLong(args.get(0));

        event.deferEdit().queue();

        event.getChannel()
            .retrieveMessageById(originalMessageId)
            .mapToResult()
            .flatMap(originalMessage -> {
                if (originalMessage.isFailure()) {
                    return event.getHook()
                        .sendMessage(
                                "Sorry, I am unable to locate the original message that contained the code, was it deleted?")
                        .setEphemeral(true);
                }

                // Restore in case bot was restarted in the meantime
                originalMessageToCodeReply.put(originalMessageId, event.getMessageIdLong());

                Optional<String> maybeCode = extractCode(originalMessage.get().getContentRaw());
                if (maybeCode.isEmpty()) {
                    return event.getHook()
                        .sendMessage(
                                "Sorry, I am unable to locate any code in the original message, was it removed?")
                        .setEphemeral(true);
                }

                List<Button> buttons =
                        event.getMessage().getButtons().stream().map(Button::asDisabled).toList();
                return event.getHook()
                    .editOriginalEmbeds(formatCodeCommand.apply(maybeCode.orElseThrow()))
                    .setActionRow(buttons);
            })
            .queue();
    }

    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        Long codeReplyMessageId = originalMessageToCodeReply.get(event.getMessageIdLong());
        if (codeReplyMessageId == null) {
            // Unknown message
            return;
        }

        String content = event.getMessage().getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            return;
        }

        // TODO Duplication with all of the editMessageEmbeds etc
        event.getChannel()
            .retrieveMessageById(codeReplyMessageId)
            .flatMap(codeReplyMessage -> codeReplyMessage
                .editMessageEmbeds(formatCodeCommand.apply(maybeCode.orElseThrow())))
            .mapToResult()
            .queue();
    }

    @Override
    public void onMessageDeleted(MessageDeleteEvent event) {
        Long codeReplyMessageId = originalMessageToCodeReply.remove(event.getMessageIdLong());
        if (codeReplyMessageId == null) {
            // Unknown message
            return;
        }

        event.getChannel().deleteMessageById(codeReplyMessageId).mapToResult().queue();
    }

    private static Optional<String> extractCode(CharSequence fullMessage) {
        Matcher codeBlockMatcher = CODE_BLOCK_EXTRACTOR_PATTERN.matcher(fullMessage);
        if (!codeBlockMatcher.find()) {
            return Optional.empty();
        }
        return Optional.of(codeBlockMatcher.group(1));
    }
}
