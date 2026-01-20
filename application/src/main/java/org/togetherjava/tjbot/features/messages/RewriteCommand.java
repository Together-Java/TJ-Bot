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
import java.util.Optional;

/**
 * The implemented command is {@code /rewrite}, which allows users to have their message rewritten
 * in a clearer, more professional, or better structured form using AI.
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

    private final ChatGptService chatGptService;

    private static ChatGptModel selectAiModel(MessageTone tone) {
        return switch (tone) {
            case CLEAR, PROFESSIONAL -> ChatGptModel.FASTEST;
            case DETAILED, TECHNICAL -> ChatGptModel.HIGH_QUALITY;
        };
    }

    private static String createAiPrompt(String userMessage, MessageTone tone) {
        return """
                You are rewriting a Discord text chat message for clarity and professionalism.
                Keep it conversational and casual—NOT email or formal document format.

                Tone: %s

                Rewrite the message to:
                - Improve clarity and structure
                - Maintain the original meaning
                - Avoid em-dashes (—)
                - Stay under %d characters (strict limit)

                If the message is already well-written, make only minor improvements.

                Message to rewrite:
                %s""".stripIndent().formatted(tone.description, MAX_MESSAGE_LENGTH, userMessage);
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

        final OptionMapping messageOption = event.getOption(MESSAGE_OPTION);

        if (messageOption == null) {
            throw new IllegalStateException("Required option '" + MESSAGE_OPTION + "' is missing");
        }

        final String userMessage = messageOption.getAsString();
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

        event.getHook().sendMessage(rewrittenText).setEphemeral(true).queue();
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

        final ChatGptModel aiModel = selectAiModel(tone);

        final String rewritePrompt = createAiPrompt(userMessage, tone);

        Optional<String> attempt = chatGptService.askRaw(rewritePrompt, aiModel);

        if (attempt.isEmpty()) {
            return attempt;
        }

        final String response = attempt.get();

        if (response.length() <= Message.MAX_CONTENT_LENGTH) {
            return attempt;
        }

        logger.debug("Rewritten message exceeded {} characters; retrying with stricter constraint",
                MAX_MESSAGE_LENGTH);

        final String shortenPrompt = rewritePrompt
                + "\n\nConstraint reminder: Your previous rewrite exceeded " + MAX_MESSAGE_LENGTH
                + " characters. Provide a revised rewrite strictly under " + MAX_MESSAGE_LENGTH
                + " characters while preserving meaning and tone.";

        return chatGptService.askRaw(shortenPrompt, aiModel);
    }

    private enum MessageTone {
        CLEAR("Clear", "Make it clear and easy to understand."),
        PROFESSIONAL("Professional", "Use a professional and polished tone."),
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
