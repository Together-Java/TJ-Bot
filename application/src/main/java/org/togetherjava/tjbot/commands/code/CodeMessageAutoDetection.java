package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.commands.utils.CodeFence;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.config.Config;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Automatically detects messages that contain code and registers them at {@link CodeMessageHandler}
 * for further processing.
 */
public final class CodeMessageAutoDetection extends MessageReceiverAdapter {
    private static final long MINIMUM_LINES_OF_CODE = 3;

    private final CodeMessageHandler codeMessageHandler;

    private final Predicate<String> isHelpForumName;

    /**
     * Creates a new instance.
     * 
     * @param config to figure out whether a message is from a help thread
     * @param codeMessageHandler to register detected code messages at for further handling
     */
    public CodeMessageAutoDetection(Config config, CodeMessageHandler codeMessageHandler) {
        super(Pattern.compile(".*"));

        this.codeMessageHandler = codeMessageHandler;

        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isWebhookMessage() || event.getAuthor().isBot() || !isHelpThread(event)) {
            return;
        }

        Message originalMessage = event.getMessage();

        Optional<CodeFence> maybeCode = MessageUtils.extractCode(originalMessage.getContentRaw());
        if (maybeCode.isEmpty()) {
            // There is no code in the message, ignore it
            return;
        }

        long amountOfCodeLines =
                maybeCode.orElseThrow().code().lines().limit(MINIMUM_LINES_OF_CODE).count();
        if (amountOfCodeLines < MINIMUM_LINES_OF_CODE) {
            return;
        }

        codeMessageHandler.addAndHandleCodeMessage(originalMessage, true);
    }

    private boolean isHelpThread(MessageReceivedEvent event) {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return false;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        String rootChannelName = thread.getParentChannel().getName();
        return isHelpForumName.test(rootChannelName);
    }
}
