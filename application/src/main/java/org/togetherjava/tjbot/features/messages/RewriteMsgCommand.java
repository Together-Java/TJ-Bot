package org.togetherjava.tjbot.features.messages;

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
 * rewritten in a clearer, more professional, or better structured form using ChatGPT AI.
 * <p>
 * The rewritten message is shown as an ephemeral message visible only to the user who triggered the
 * command, making it perfect for getting quick writing improvements without cluttering the channel.
 * <p>
 * Users can optionally specify a tone/style for the rewrite. If not provided, defaults to CLEAR.
 */
public final class RewriteMsgCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RewriteMsgCommand.class);
    public static final String COMMAND_NAME = "rewrite-msg";
    private static final String MESSAGE_OPTION = "message";
    private static final String TONE_OPTION = "tone";
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MIN_MESSAGE_LENGTH = 3;
    private static final ChatGptModel CHAT_GPT_MODEL = ChatGptModel.HIGH_QUALITY;

    private final ChatGptService chatGptService;

    private static String buildResponse(String userMessage, String rewrittenMessage, MsgTone tone) {
        final String toneLabel = tone.displayName;

        return """
                **Rewritten message (%s)**

                **Original:**
                %s

                **Rewritten:**
                %s""".formatted(toneLabel, userMessage, rewrittenMessage);
    }

    private static String buildChatGptPrompt(String userMessage, MsgTone tone) {
        return """
                Please rewrite the following message to make it clearer, more professional, \
                and better structured. Maintain the original meaning while improving the quality \
                of the writing. Do NOT use em-dashes (—). %s

                If the message is already well-written, provide minor improvements.

                Original message:
                %s""".formatted(tone.description, userMessage);
    }

    /**
     * Creates the slash command definition and configures available options for rewriting messages.
     *
     * @param chatGptService service for interacting with ChatGPT
     */
    public RewriteMsgCommand(ChatGptService chatGptService) {
        super(COMMAND_NAME, "Let AI rephrase and improve your message", CommandVisibility.GUILD);

        this.chatGptService = chatGptService;

        final OptionData messageOption =
                new OptionData(OptionType.STRING, MESSAGE_OPTION, "The message you want to rewrite",
                        true)
                    .setMinLength(MIN_MESSAGE_LENGTH)
                    .setMaxLength(MAX_MESSAGE_LENGTH);

        final OptionData toneOption = new OptionData(OptionType.STRING, TONE_OPTION,
                "The tone/style for the rewritten message (default: " + MsgTone.CLEAR.displayName
                        + ")",
                false);

        Arrays.stream(MsgTone.values())
            .forEach(tone -> toneOption.addChoice(tone.displayName, tone.name()));

        getData().addOptions(messageOption, toneOption);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        final String userMessage =
                Objects.requireNonNull(event.getOption(MESSAGE_OPTION)).getAsString();

        final MsgTone tone = parseTone(event.getOption(TONE_OPTION), event.getUser().getId());

        event.deferReply(true).queue();

        final Optional<String> rewrittenMessage = this.rewrite(userMessage, tone);

        if (rewrittenMessage.isEmpty()) {
            logger.debug("Failed to obtain a response for /rewrite-msg, original message: '{}'",
                    userMessage);
            event.getHook()
                .editOriginal(
                        "An error occurred while processing your request. Please try again later.")
                .queue();
            return;
        }

        final String response = buildResponse(userMessage, rewrittenMessage.orElseThrow(), tone);

        event.getHook().editOriginal(response).queue();
    }

    private MsgTone parseTone(@Nullable OptionMapping toneOption, String userId)
            throws IllegalArgumentException {
        if (toneOption == null) {
            logger.debug("Tone option not provided for user: {}, using default CLEAR", userId);
            return MsgTone.CLEAR;
        }

        final String toneValue = toneOption.getAsString();
        final MsgTone tone = MsgTone.valueOf(toneValue);

        logger.debug("Parsed tone '{}' for user: {}", tone.displayName, userId);

        return tone;
    }

    private Optional<String> rewrite(String userMessage, MsgTone tone) {
        final String rewritePrompt = buildChatGptPrompt(userMessage, tone);

        return chatGptService.ask(rewritePrompt, tone.displayName, CHAT_GPT_MODEL);
    }

    private enum MsgTone {
        CLEAR("Clear", "Make it clear and easy to understand."),
        PRO("Pro", "Use a professional and polished tone."),
        DETAILED("Detailed", "Expand with more detail and explanation."),
        TECHNICAL("Technical", "Use technical and specialized language where appropriate.");

        private final String displayName;
        private final String description;

        MsgTone(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
}
