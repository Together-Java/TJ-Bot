package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

import org.togetherjava.tjbot.features.MessageReceiverAdapter;
import org.togetherjava.tjbot.features.help.HelpSystemHelper;

import java.io.InputStream;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Listens to messages that are likely supposed to be message commands, such as {@code !foo} and
 * then educates the user about using slash commands, such as {@code /foo} instead.
 */
public final class SlashCommandEducator extends MessageReceiverAdapter {
    private static final int MAX_COMMAND_LENGTH = 30;
    private static final String SLASH_COMMAND_POPUP_ADVICE_PATH = "slashCommandPopupAdvice.png";
    private static final Predicate<String> IS_MESSAGE_COMMAND = Pattern.compile("""
            [.!?] #Start of message command
            [a-zA-Z]{2,15} #Name of message command, e.g. 'close'
            .*[^);] #Rest of the message (don't end with code stuff)
            """, Pattern.COMMENTS).asMatchPredicate();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String content = event.getMessage().getContentRaw();

        if (IS_MESSAGE_COMMAND.test(content) && content.length() < MAX_COMMAND_LENGTH) {
            sendAdvice(event.getMessage());
        }
    }

    private void sendAdvice(Message message) {
        String content =
                """
                        Looks like you attempted to use a command? Please note that we only use **slash-commands** on this server 🙂

                        Try starting your message with a forward-slash `/` and Discord should open a popup showing you all available commands.
                        A command might then look like `/foo` 👍""";

        createReply(message, content).queue();
    }

    private static MessageCreateAction createReply(Message messageToReplyTo, String content) {
        boolean useImage = true;
        InputStream imageData =
                HelpSystemHelper.class.getResourceAsStream("/" + SLASH_COMMAND_POPUP_ADVICE_PATH);
        if (imageData == null) {
            useImage = false;
        }

        MessageEmbed embed = new EmbedBuilder().setDescription(content)
            .setImage(useImage ? "attachment://" + SLASH_COMMAND_POPUP_ADVICE_PATH : null)
            .build();

        MessageCreateAction action = messageToReplyTo.replyEmbeds(embed);
        if (useImage) {
            action = action
                .addFiles(FileUpload.fromData(imageData, SLASH_COMMAND_POPUP_ADVICE_PATH));
        }

        return action;
    }
}
