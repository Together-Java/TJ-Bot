package org.togetherjava.tjbot.commands.code;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
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
import org.togetherjava.tjbot.commands.utils.CodeFence;
import org.togetherjava.tjbot.commands.utils.MessageUtils;

import javax.annotation.Nullable;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handler that detects code in messages and offers code actions to the user, such as formatting
 * their code.
 * <p>
 * Code actions are automatically updated whenever the code in the original message is edited or
 * deleted.
 */
public final class CodeMessageHandler extends MessageReceiverAdapter implements UserInteractor {
    private static final Logger logger = LoggerFactory.getLogger(CodeMessageHandler.class);

    private static final String DELETE_CUE = "delete";

    static final Color AMBIENT_COLOR = Color.decode("#FDFD96");

    private final ComponentIdInteractor componentIdInteractor;
    private final Map<String, CodeAction> labelToCodeAction;

    /**
     * Memorizes the ID of the bots code-reply message that a message belongs to. That way, the
     * code-reply can be retrieved and managed easily when the original message is edited or
     * deleted. Losing this cache, for example during bot-restart, effectively disables this
     * update-feature for old messages.
     * <p>
     * The feature is secondary though, which is why its kept in RAM and not in the DB.
     */
    private final Cache<Long, Long> originalMessageToCodeReply =
            Caffeine.newBuilder().maximumSize(2_000).build();

    /**
     * Creates a new instance.
     */
    public CodeMessageHandler() {
        super(Pattern.compile(".*"));

        componentIdInteractor = new ComponentIdInteractor(getInteractionType(), getName());

        List<CodeAction> codeActions = List.of(new FormatCodeCommand());

        labelToCodeAction = codeActions.stream()
            .collect(Collectors.toMap(CodeAction::getLabel, Function.identity(), (x, y) -> y,
                    LinkedHashMap::new));
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

        Optional<CodeFence> maybeCode = MessageUtils.extractCode(content);
        if (maybeCode.isEmpty()) {
            // There is no code in the message, ignore it
            return;
        }

        // Suggest code actions and remember the message <-> reply
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
            @Nullable CodeAction currentlyActiveAction) {
        Stream<Button> codeActionButtons = labelToCodeAction.values().stream().map(action -> {
            Button button = createButtonForAction(action, originalMessageId);
            return action == currentlyActiveAction ? button.asDisabled() : button;
        });

        Stream<Button> otherButtons = Stream.of(createDeleteButton(originalMessageId));

        return Stream.concat(codeActionButtons, otherButtons).toList();
    }

    private Button createDeleteButton(long originalMessageId) {
        return Button.danger(componentIdInteractor.generateComponentId(
                Long.toString(originalMessageId), "", DELETE_CUE), Emoji.fromUnicode("🗑"));
    }

    private Button createButtonForAction(CodeAction action, long originalMessageId) {
        return Button.primary(
                componentIdInteractor.generateComponentId(Long.toString(originalMessageId)),
                action.getLabel());
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        long originalMessageId = Long.parseLong(args.get(0));
        if (args.size() >= 3 && DELETE_CUE.equals(args.get(2))) {
            deleteCodeReply(event, originalMessageId);
            return;
        }

        CodeAction codeAction = getActionOfEvent(event);
        event.deferEdit().queue();

        // User decided for an action, apply it to the code
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

                // If the bot got restarted in the meantime, it forgot about the message
                // since we have the context here, we can restore that information
                originalMessageToCodeReply.put(originalMessageId, event.getMessageIdLong());

                Optional<CodeFence> maybeCode =
                        MessageUtils.extractCode(originalMessage.get().getContentRaw());
                if (maybeCode.isEmpty()) {
                    return event.getHook()
                        .sendMessage(
                                "Sorry, I am unable to locate any code in the original message, was it removed?")
                        .setEphemeral(true);
                }

                // Apply the selected action
                return event.getHook()
                    .editOriginalEmbeds(codeAction.apply(maybeCode.orElseThrow()))
                    .setActionRow(createButtons(originalMessageId, codeAction));
            })
            .queue();
    }

    private void deleteCodeReply(ButtonInteractionEvent event, long originalMessageId) {
        originalMessageToCodeReply.invalidate(originalMessageId);
        event.getMessage().delete().queue();
    }

    private CodeAction getActionOfEvent(ButtonInteractionEvent event) {
        return labelToCodeAction.get(event.getButton().getLabel());
    }

    @Override
    public void onMessageUpdated(MessageUpdateEvent event) {
        long originalMessageId = event.getMessageIdLong();

        Long codeReplyMessageId = originalMessageToCodeReply.getIfPresent(originalMessageId);
        if (codeReplyMessageId == null) {
            // Some unrelated non-code message was edited
            return;
        }

        // Edit the code reply as well by re-applying the current action
        String content = event.getMessage().getContentRaw();

        Optional<CodeFence> maybeCode = MessageUtils.extractCode(content);
        if (maybeCode.isEmpty()) {
            // The original message had code, but now the code was removed
            return;
        }

        event.getChannel().retrieveMessageById(codeReplyMessageId).flatMap(codeReplyMessage -> {
            Optional<CodeAction> maybeCodeAction = getCurrentActionFromCodeReply(codeReplyMessage);
            if (maybeCodeAction.isEmpty()) {
                // The user did not decide on an action yet, nothing to update
                return new CompletedRestAction<>(event.getJDA(), null);
            }

            // Re-apply the current action
            return codeReplyMessage
                .editMessageEmbeds(maybeCodeAction.orElseThrow().apply(maybeCode.orElseThrow()));
        }).queue(any -> {
        }, failure -> logger.warn(
                "Attempted to update a code-reply-message ({}), but failed. The original code-message was {}",
                codeReplyMessageId, originalMessageId, failure));
    }

    private Optional<CodeAction> getCurrentActionFromCodeReply(Message codeReplyMessage) {
        // The disabled action is the currently applied action
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
            // Some unrelated non-code message was deleted
            return;
        }

        // Delete the code reply as well
        originalMessageToCodeReply.invalidate(originalMessageId);

        event.getChannel().deleteMessageById(codeReplyMessageId).queue(any -> {
        }, failure -> logger.warn(
                "Attempted to delete a code-reply-message ({}), but failed. The original code-message was {}",
                codeReplyMessageId, originalMessageId, failure));
    }
}
