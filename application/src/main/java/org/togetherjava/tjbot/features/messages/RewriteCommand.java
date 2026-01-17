package org.togetherjava.tjbot.features.messages;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.chatgpt.ChatGptModel;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The implemented command is {@code /rewrite-msg}, which allows users to have their message
 * rewritten in a clearer, more professional, or better structured form using AI.
 * <p>
 * The rewritten message is shown as an ephemeral message visible only to the user who triggered the
 * command.
 * <p>
 * Users can optionally specify a tone/style for the rewrite.
 */
public final class RewriteCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RewriteCommand.class);
    private static final String COMMAND_NAME = "rewrite";
    private static final String MESSAGE_OPTION = "message";
    private static final String TONE_OPTION = "tone";

    private static final int MAX_MESSAGE_LENGTH = Message.MAX_CONTENT_LENGTH;
    private static final int MIN_MESSAGE_LENGTH = 3;
    private static final ChatGptModel CHAT_GPT_MODEL = ChatGptModel.FASTEST;

    private final ChatGptService chatGptService;

    private static String createAiPrompt(String userMessage, MessageTone tone) {
        return """
                Rewrite the following message to make it clearer, more professional, \
                and better structured. Maintain the original meaning while improving the quality \
                of the writing. Do NOT use em-dashes (—). %s

                IMPORTANT: The rewritten text MUST be no more than 2000 characters. \
                If needed, compress wording while preserving key details and intent.

                If the message is already well-written, provide minor improvements.

                Original message:
                %s""".stripIndent().formatted(tone.description, userMessage);
    }

    private static String buildOriginalMsgResponse(String userMessage, MessageTone tone) {
        return """
                **Original message (%s)**

                %s
                """.stripIndent().formatted(tone.displayName, userMessage);
    }

    private static String buildRewrittenMsgResponse(String aiMessage, MessageTone tone) {
        return """
                **Rewritten message (%s)**

                %s
                """.stripIndent().formatted(tone.displayName, aiMessage);
    }

    /**
     * Creates the slash command definition and configures available options for rewriting messages.
     *
     * @param chatGptService service for interacting with ChatGPT
     */
    public RewriteCommand(ChatGptService chatGptService) {
        super(COMMAND_NAME, "Let AI rephrase and improve your message", CommandVisibility.GUILD);

        this.chatGptService = chatGptService;

        final OptionData messageOption =
                new OptionData(OptionType.STRING, MESSAGE_OPTION, "The message you want to rewrite",
                        true)
                    .setMinLength(MIN_MESSAGE_LENGTH)
                    .setMaxLength(MAX_MESSAGE_LENGTH);

        final OptionData toneOption = new OptionData(OptionType.STRING, TONE_OPTION,
                "The tone/style for the rewritten message (default: "
                        + MessageTone.CLEAR.displayName + ")",
                false);

        Arrays.stream(MessageTone.values())
            .forEach(tone -> toneOption.addChoice(tone.displayName, tone.name()));

        getData().addOptions(messageOption, toneOption);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {

        final String userMessage =
                Objects.requireNonNull(event.getOption(MESSAGE_OPTION)).getAsString();
        final MessageTone tone = parseTone(event.getOption(TONE_OPTION));

        event.deferReply(true).queue();

        Optional<String> rewrittenMessage = rewrite(userMessage, tone);

        if (rewrittenMessage.isEmpty()) {
            logger.debug("Failed to obtain a response for /{}, original message: '{}'",
                    COMMAND_NAME, userMessage);

            event.getHook()
                .editOriginal(
                        "An error occurred while processing your request. Please try again later.")
                .queue();

            return;
        }

        final String rewrittenText = rewrittenMessage.orElseThrow();

        logger.debug("Rewrite successful; rewritten message length: {}", rewrittenText.length());

        event.getHook()
            .sendMessage(buildOriginalMsgResponse(userMessage, tone))
            .setEphemeral(true)
            .queue();

        event.getHook()
            .sendMessage(buildRewrittenMsgResponse(rewrittenText, tone))
            .setEphemeral(true)
            .queue();
    }

    private MessageTone parseTone(@Nullable OptionMapping toneOption)
            throws IllegalArgumentException {

        if (toneOption == null) {
            logger.debug("Tone option not provided, using default '{}'", MessageTone.CLEAR.name());
            return MessageTone.CLEAR;
        }

        final String toneValue = toneOption.getAsString();

        return MessageTone.valueOf(toneValue);
    }

    private Optional<String> rewrite(String userMessage, MessageTone tone) {

        final String rewritePrompt = createAiPrompt(userMessage, tone);

        Optional<String> attempt =
                chatGptService.ask(rewritePrompt, tone.displayName, CHAT_GPT_MODEL);

        if (attempt.isEmpty()) {
            return attempt;
        }

        final String response = attempt.get();

        if (response.length() <= Message.MAX_CONTENT_LENGTH) {
            return attempt;
        }

        logger.debug("Rewritten message exceeded {} characters; retrying with stricter constraint",
                Message.MAX_CONTENT_LENGTH);

        final String shortenPrompt = rewritePrompt
                + "\n\nConstraint reminder: Your previous rewrite exceeded "
                + Message.MAX_CONTENT_LENGTH
                + " characters. Provide a revised rewrite strictly under "
                + Message.MAX_CONTENT_LENGTH + " characters while preserving meaning and tone.";

        return chatGptService.ask(shortenPrompt, tone.displayName, CHAT_GPT_MODEL);
    }

    private enum MessageTone {
        CLEAR("Clear", "Make it clear and easy to understand."),
        PRO("Pro", "Use a professional and polished tone."),
        DETAILED("Detailed", "Expand with more detail and explanation."),
        TECHNICAL("Technical", "Use technical and specialized language where appropriate.");

        private final String displayName;
        private final String description;

        MessageTone(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
}
