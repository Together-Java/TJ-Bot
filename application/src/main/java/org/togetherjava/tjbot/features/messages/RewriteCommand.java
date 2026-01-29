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

    private static final String AI_REWRITE_PROMPT_TEMPLATE = """
            You are rewriting a Discord text chat message for clarity and professionalism.
            Keep it conversational and casual, not email or formal document format.

            Tone: %s

            Rewrite the message to:
            - Improve clarity and structure
            - Maintain the original meaning
            - Avoid em-dashes (—)
            - Stay under %d characters (strict limit)

            If the message is already well-written, make only minor improvements.

            Reply with ONLY the rewritten message, nothing else (greetings, preamble, etc).

            Message to rewrite:
            %s
            """.stripIndent();

    private final ChatGptService chatGptService;

    /**
     * Creates the slash command definition and configures available options for rewriting messages.
     *
     * @param chatGptService service for interacting with ChatGPT
     */
    public RewriteCommand(ChatGptService chatGptService) {
        super(COMMAND_NAME, "Let AI rephrase and improve your message", CommandVisibility.GUILD);

        this.chatGptService = chatGptService;

        OptionData messageOption =
                new OptionData(OptionType.STRING, MESSAGE_OPTION, "The message you want to rewrite",
                        true)
                    .setMinLength(MIN_MESSAGE_LENGTH)
                    .setMaxLength(MAX_MESSAGE_LENGTH);

        OptionData toneOption = new OptionData(OptionType.STRING, TONE_OPTION,
                "The tone/style for the rewritten message (default: "
                        + MessageTone.CLEAR.displayName + ")",
                false);

        Arrays.stream(MessageTone.values())
            .forEach(tone -> toneOption.addChoice(tone.displayName, tone.name()));

        getData().addOptions(messageOption, toneOption);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {

        OptionMapping messageOption = event.getOption(MESSAGE_OPTION);

        if (messageOption == null) {
            throw new IllegalArgumentException(
                    "Required option '" + MESSAGE_OPTION + "' is missing");
        }

        String userMessage = messageOption.getAsString();

        MessageTone tone = parseTone(event.getOption(TONE_OPTION));

        event.deferReply(true).queue();

        String rewrittenMessage = rewrite(userMessage, tone);

        if (rewrittenMessage.isEmpty()) {
            logger.debug("Failed to obtain a response for /{}, original message: '{}'",
                    COMMAND_NAME, userMessage);

            event.getHook()
                .editOriginal(
                        "An error occurred while processing your request. Please try again later.")
                .queue();

            return;
        }

        logger.debug("Rewrite successful; rewritten message length: {}", rewrittenMessage.length());

        event.getHook().sendMessage(rewrittenMessage).setEphemeral(true).queue();
    }

    private MessageTone parseTone(@Nullable OptionMapping toneOption)
            throws IllegalArgumentException {

        if (toneOption == null) {
            logger.debug("Tone option not provided, using default '{}'", MessageTone.CLEAR.name());
            return MessageTone.CLEAR;
        }

        return MessageTone.valueOf(toneOption.getAsString());
    }

    private String rewrite(String userMessage, MessageTone tone) {

        String rewritePrompt = createAiPrompt(userMessage, tone);

        ChatGptModel aiModel = tone.model;

        String attempt = askAi(rewritePrompt, aiModel);

        if (attempt.length() <= MAX_MESSAGE_LENGTH) {
            return attempt;
        }

        logger.debug("Rewritten message exceeded {} characters; retrying with stricter constraint",
                MAX_MESSAGE_LENGTH);

        String shortenPrompt =
                """
                        %s

                        Constraint reminder: Your previous rewrite exceeded %d characters.
                        Provide a revised rewrite strictly under %d characters while preserving meaning and tone.
                        """
                    .formatted(rewritePrompt, MAX_MESSAGE_LENGTH, MAX_MESSAGE_LENGTH);

        return askAi(shortenPrompt, aiModel);
    }

    private String askAi(String shortenPrompt, ChatGptModel aiModel) {
        return chatGptService.askRaw(shortenPrompt, aiModel).orElse("");
    }

    private static String createAiPrompt(String userMessage, MessageTone tone) {
        return AI_REWRITE_PROMPT_TEMPLATE.formatted(tone.description, MAX_MESSAGE_LENGTH,
                userMessage);
    }

    private enum MessageTone {
        CLEAR("Clear", "Make it clear and easy to understand.", ChatGptModel.FASTEST),
        PROFESSIONAL("Professional", "Use a professional and polished tone.", ChatGptModel.FASTEST),
        DETAILED("Detailed", "Expand with more detail and explanation.", ChatGptModel.HIGH_QUALITY),
        TECHNICAL("Technical", "Use technical and specialized language where appropriate.",
                ChatGptModel.HIGH_QUALITY);

        private final String displayName;
        private final String description;
        private final ChatGptModel model;

        MessageTone(String displayName, String description, ChatGptModel model) {
            this.displayName = displayName;
            this.description = description;
            this.model = model;
        }
    }

}
