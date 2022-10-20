package org.togetherjava.tjbot.commands.code;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.commands.UserInteractionType;
import org.togetherjava.tjbot.commands.UserInteractor;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.commands.componentids.ComponentIdInteractor;

import javax.annotation.Nullable;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CodeMessageHandler extends MessageReceiverAdapter implements UserInteractor {
    private static final Logger logger = LoggerFactory.getLogger(CodeMessageHandler.class);

    static final Color AMBIENT_COLOR = Color.decode("#FDFD96");
    // TODO doc, lol
    private static final Pattern CODE_BLOCK_EXTRACTOR_PATTERN =
            Pattern.compile("```(?:java)?\\s*([\\w\\W]+)```|``?([\\w\\W]+)``?");

    private final ComponentIdInteractor componentIdInteractor;
    private final Map<String, CodeAction> labelToCodeAction;

    private final Cache<Long, Long> originalMessageToCodeReply =
            Caffeine.newBuilder().maximumSize(10_000).build();

    public CodeMessageHandler() {
        super(Pattern.compile(".*"));

        componentIdInteractor = new ComponentIdInteractor(getInteractionType(), getName());

        labelToCodeAction =
                Stream.of(new FormatCodeCommand(), new RunCodeCommand(), new BytecodeCommand())
                    .collect(Collectors.toMap(CodeAction::getLabel, Function.identity(),
                            (x, y) -> y, LinkedHashMap::new));
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
    public void onSelectMenuSelection(SelectMenuInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }

        Message originalMessage = event.getMessage();
        String content = originalMessage.getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            return;
        }

        originalMessage.reply(createCodeReplyMessage(originalMessage.getIdLong()))
            .onSuccess(replyMessage -> originalMessageToCodeReply.put(originalMessage.getIdLong(),
                    replyMessage.getIdLong()))
            .queue();
    }

    private MessageCreateData createCodeReplyMessage(long originalMessageId) {
        return new MessageCreateBuilder().setContent("Detected code, here are some useful tools:")
            .setActionRow(createButtons(originalMessageId, null))
            .build();
    }

    private List<Button> createButtons(long originalMessageId,
            @Nullable CodeAction disabledAction) {
        return labelToCodeAction.values().stream().map(action -> {
            Button button = createButtonForAction(action, originalMessageId);
            return action == disabledAction ? button.asDisabled() : button;
        }).toList();
    }

    private Button createButtonForAction(CodeAction action, long originalMessageId) {
        return Button.primary(
                componentIdInteractor.generateComponentId(Long.toString(originalMessageId)),
                action.getLabel());
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        long originalMessageId = Long.parseLong(args.get(0));
        CodeAction codeAction = getActionOfEvent(event);

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

                return event.getHook()
                    .editOriginalEmbeds(codeAction.apply(maybeCode.orElseThrow()))
                    .setActionRow(createButtons(originalMessageId, codeAction));
            })
            .queue();
    }

    private CodeAction getActionOfEvent(ButtonInteractionEvent event) {
        return labelToCodeAction.get(event.getButton().getLabel());
    }

    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        long originalMessageId = event.getMessageIdLong();

        Long codeReplyMessageId = originalMessageToCodeReply.getIfPresent(originalMessageId);
        if (codeReplyMessageId == null) {
            // Unknown message
            return;
        }

        String content = event.getMessage().getContentRaw();

        Optional<String> maybeCode = extractCode(content);
        if (maybeCode.isEmpty()) {
            return;
        }

        event.getChannel().retrieveMessageById(codeReplyMessageId).flatMap(codeReplyMessage -> {
            Optional<CodeAction> maybeCodeAction = getCurrentActionFromCodeReply(codeReplyMessage);
            if (maybeCodeAction.isEmpty()) {
                // No action was clicked yet
                return new CompletedRestAction<>(event.getJDA(), null);
            }

            return codeReplyMessage
                .editMessageEmbeds(maybeCodeAction.orElseThrow().apply(maybeCode.orElseThrow()));
        }).queue(any -> {
        }, failure -> logger.warn(
                "Attempted to update a code-reply-message ({}), but failed. The original code-message was {}",
                codeReplyMessageId, originalMessageId, failure));
    }

    private Optional<CodeAction> getCurrentActionFromCodeReply(Message codeReplyMessage) {
        return codeReplyMessage.getButtons()
            .stream()
            .filter(Button::isDisabled)
            .map(Button::getLabel)
            .map(labelToCodeAction::get)
            .findAny();
    }

    @Override
    public void onMessageDeleted(MessageDeleteEvent event) {
        long originalMessageId = event.getMessageIdLong();

        Long codeReplyMessageId = originalMessageToCodeReply.getIfPresent(originalMessageId);
        if (codeReplyMessageId == null) {
            // Unknown message
            return;
        }
        originalMessageToCodeReply.invalidate(codeReplyMessageId);

        event.getChannel().deleteMessageById(codeReplyMessageId).queue(any -> {
        }, failure -> logger.warn(
                "Attempted to delete a code-reply-message ({}), but failed. The original code-message was {}",
                codeReplyMessageId, originalMessageId, failure));
    }

    private static Optional<String> extractCode(CharSequence fullMessage) {
        Matcher codeBlockMatcher = CODE_BLOCK_EXTRACTOR_PATTERN.matcher(fullMessage);
        if (!codeBlockMatcher.find()) {
            return Optional.empty();
        }
        return Optional.of(codeBlockMatcher.group(1));
    }
}
