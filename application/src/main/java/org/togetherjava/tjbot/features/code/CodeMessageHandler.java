package org.togetherjava.tjbot.features.code;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.FeatureBlacklist;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;
import org.togetherjava.tjbot.features.jshell.JShellEval;
import org.togetherjava.tjbot.features.utils.CodeFence;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles code in registered messages and offers code actions to the user, such as formatting their
 * code.
 * <p>
 * Messages can be registered by using {@link #addAndHandleCodeMessage(Message, boolean)}.
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
     *
     * @param blacklist the feature blacklist, used to test if certain code actions should be
     *        disabled
     * @param jshellEval used to execute java code and build visual result
     */
    public CodeMessageHandler(FeatureBlacklist<String> blacklist, JShellEval jshellEval) {
        componentIdInteractor = new ComponentIdInteractor(getInteractionType(), getName());

        List<CodeAction> codeActions = blacklist
            .filterStream(Stream.of(new FormatCodeCommand(), new EvalCodeCommand(jshellEval)),
                    codeAction -> codeAction.getClass().getName())
            .toList();

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
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
    }

    /**
     * Adds the given message to the code messages handled by this instance. Also sends the
     * corresponding code-reply to the author.
     *
     * @param originalMessage the code message to add to this handler
     * @param showDeleteButton whether the code-actions should initially have a delete button or not
     */
    public void addAndHandleCodeMessage(Message originalMessage, boolean showDeleteButton) {
        // Suggest code actions and remember the message <-> reply
        MessageCreateData codeReply =
                createCodeReplyMessage(originalMessage.getIdLong(), showDeleteButton);

        originalMessage.reply(codeReply)
            .onSuccess(replyMessage -> originalMessageToCodeReply.put(originalMessage.getIdLong(),
                    replyMessage.getIdLong()))
            .queue();
    }

    private MessageCreateData createCodeReplyMessage(long originalMessageId,
            boolean showDeleteButton) {
        List<Button> codeActionButtons = new ArrayList<>(createButtons(originalMessageId, null));
        if (showDeleteButton) {
            codeActionButtons.add(createDeleteButton(originalMessageId));
        }

        return new MessageCreateBuilder().setContent("Detected code, here are some useful tools:")
            .setActionRow(codeActionButtons)
            .build();
    }

    private List<Button> createButtons(long originalMessageId,
            @Nullable CodeAction currentlyActiveAction) {
        return labelToCodeAction.values().stream().map(action -> {
            Button button = createButtonForAction(action, originalMessageId);
            return action == currentlyActiveAction ? button.asDisabled() : button;
        }).toList();
    }

    private Button createDeleteButton(long originalMessageId) {
        String noCodeActionLabel = "";
        return Button.danger(componentIdInteractor.generateComponentId(
                Long.toString(originalMessageId), noCodeActionLabel, DELETE_CUE), "Dismiss");
    }

    private Button createButtonForAction(CodeAction action, long originalMessageId) {
        return Button.primary(
                componentIdInteractor.generateComponentId(Long.toString(originalMessageId)),
                action.getLabel());
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        long originalMessageId = Long.parseLong(args.getFirst());

        event.deferEdit().queue();

        // The third arg indicates a non-code-action button
        if (args.size() >= 3 && DELETE_CUE.equals(args.get(2))) {
            deleteCodeReply(event, originalMessageId);
            return;
        }

        CodeAction codeAction = getActionOfEvent(event);

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

                CodeFence code = extractCodeOrFallback(originalMessage.get().getContentRaw());

                // Apply the selected action
                return event.getHook()
                    .editOriginalEmbeds(codeAction.apply(code))
                    .setActionRow(createButtons(originalMessageId, codeAction));
            })
            .queue();
    }

    private void deleteCodeReply(ButtonInteractionEvent event, long originalMessageId) {
        logger.debug("User {} deleted the code-reply from original message {} in channel {}",
                event.getUser().getId(), originalMessageId, event.getChannel().getName());

        originalMessageToCodeReply.invalidate(originalMessageId);
        event.getHook().deleteOriginal().queue();
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
        CodeFence code = extractCodeOrFallback(event.getMessage().getContentRaw());

        event.getChannel().retrieveMessageById(codeReplyMessageId).flatMap(codeReplyMessage -> {
            Optional<CodeAction> maybeCodeAction = getCurrentActionFromCodeReply(codeReplyMessage);
            if (maybeCodeAction.isEmpty()) {
                // The user did not decide on an action yet, nothing to update
                return new CompletedRestAction<>(event.getJDA(), null);
            }

            // Re-apply the current action
            return codeReplyMessage.editMessageEmbeds(maybeCodeAction.orElseThrow().apply(code));
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

    private static CodeFence extractCodeOrFallback(String content) {
        return MessageUtils.extractCode(content).orElseGet(() -> new CodeFence("java", content));
    }
}
