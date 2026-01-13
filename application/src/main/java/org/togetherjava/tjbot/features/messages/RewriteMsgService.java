package org.togetherjava.tjbot.features.messages;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.chatgpt.ChatGptModel;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;
import org.togetherjava.tjbot.features.help.HelpSystemHelper;

import java.util.Optional;

/**
 * Service for handling rewrite command business logic and ChatGPT integration.
 */
public class RewriteMsgService {
    private static final Logger logger = LoggerFactory.getLogger(RewriteMsgService.class);
    private static final ChatGptModel CHAT_GPT_MODEL = ChatGptModel.HIGH_QUALITY;

    private final ChatGptService chatGptService;
    private final HelpSystemHelper helper;

    /**
     * Creates a new RewriteMsgService.
     *
     * @param chatGptService the ChatGPT service
     * @param helper the help system helper for embed formatting
     */
    public RewriteMsgService(ChatGptService chatGptService, HelpSystemHelper helper) {
        this.chatGptService = chatGptService;
        this.helper = helper;
    }

    public String validateMsg(@Nullable OptionMapping messageOption, String userId) {
        logger.debug("Extracting message option for user: {}", userId);
        logger.debug("Retrieved message option: {}", messageOption != null ? "present" : "null");

        final String userMessage = messageOption != null ? messageOption.getAsString() : "";

        if (userMessage.isEmpty()) {
            logger.warn("User {} provided an empty message", userId);
        } else {
            logger.debug("User {} provided message of length: {}", userId, userMessage.length());
            logMessagePreview(userMessage);
        }

        return userMessage;
    }

    public RewriteMsgTone parseTone(@Nullable OptionMapping toneOption, String userId) {
        logger.debug("Extracting tone option for user: {}", userId);
        logger.debug("Retrieved tone option: {}", toneOption != null ? "present" : "null");

        if (toneOption == null) {
            logger.debug("Tone option not provided, using default: {}",
                    RewriteMsgTone.CLEAR.getDisplayName());
            return RewriteMsgTone.CLEAR;
        }

        final String toneValue = toneOption.getAsString();
        try {
            final RewriteMsgTone tone = RewriteMsgTone.valueOf(toneValue);
            logger.debug("Parsed tone value from option: {}", toneValue);
            return tone;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid tone value provided: {}, using default CLEAR", toneValue, e);
            return RewriteMsgTone.CLEAR;
        }
    }

    public Optional<String> rewrite(String userMessage, RewriteMsgTone tone, String userId) {
        logger.debug("Rewriting message for user {} with tone: {}", userId, tone.getDisplayName());

        final String rewritePrompt = buildChatGptPrompt(userMessage, tone);
        logger.debug("ChatGPT prompt prepared: {} characters", rewritePrompt.length());

        try {
            final Optional<String> rewrittenMessage = chatGptService.ask(rewritePrompt,
                    "Professional writing improvement", CHAT_GPT_MODEL);

            if (rewrittenMessage.isPresent()) {
                logger.info("Successfully rewrote message for user: {} with tone: {}", userId,
                        tone.getDisplayName());
                logMessagePreview(rewrittenMessage.get());
            } else {
                logger.warn("ChatGPT returned empty response for user: {}", userId);
            }

            return rewrittenMessage;
        } catch (Exception e) {
            logger.error("Failed to rewrite message for user: {}", userId, e);
            return Optional.empty();
        }
    }

    public Optional<MessageEmbed> buildResponse(String userMessage,
            @Nullable String rewrittenMessage, RewriteMsgTone tone, String userId,
            SelfUser selfUser) {
        logger.debug("Building response embed for user: {}", userId);

        final String responseContent = rewrittenMessage != null ? rewrittenMessage
                : "Sorry, I couldn't rewrite your message at this time. Please try again later.";
        final String embedTitle = "Rewritten message (" + tone.getDisplayName() + ")";
        logger.debug("Prepared embed title: {}", embedTitle);

        try {
            final MessageEmbed responseEmbed = helper.generateGptResponseEmbed(
                    "**Original:**\n" + userMessage + "\n\n**Rewritten:**\n" + responseContent,
                    selfUser, embedTitle, CHAT_GPT_MODEL);
            logger.debug("Message embed created successfully for user: {}", userId);
            return Optional.of(responseEmbed);
        } catch (Exception e) {
            logger.error("Failed to create message embed for user: {}", userId, e);
            return Optional.empty();
        }
    }

    private String buildChatGptPrompt(String userMessage, RewriteMsgTone tone) {
        return """
                Please rewrite the following message to make it clearer, more professional, \
                and better structured. Maintain the original meaning while improving the quality \
                of the writing. Do NOT use em-dashes (—). %s

                If the message is already well-written, provide minor improvements.

                Original message:
                %s""".formatted(tone.getPromptInstruction(), userMessage);
    }

    private void logMessagePreview(String message) {
        final int previewLength = Math.min(50, message.length());
        final String preview = message.substring(0, previewLength);

        logger.debug("Message content preview: {}", preview);
    }
}
