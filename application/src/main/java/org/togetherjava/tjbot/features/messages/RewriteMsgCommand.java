package org.togetherjava.tjbot.features.messages;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.util.Arrays;
import java.util.Optional;

/**
 * The implemented command is {@code /rewrite}, which allows users to have their message rewritten
 * in a clearer, more professional, or better structured form using ChatGPT AI.
 * <p>
 * The rewritten message is shown as an ephemeral message visible only to the user who triggered the
 * command, making it perfect for getting quick writing improvements without cluttering the channel.
 * <p>
 * Users can optionally specify a tone/style for the rewrite. If not provided, defaults to CLEAR.
 */
public final class RewriteMsgCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RewriteMsgCommand.class);
    public static final String COMMAND_NAME = "rewrite";
    private static final String MESSAGE_OPTION = "message";
    private static final String TONE_OPTION = "tone";
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MIN_MESSAGE_LENGTH = 3;

    private final RewriteMsgService rewriteMsgService;


    public RewriteMsgCommand(RewriteMsgService rewriteMsgService) {
        super(COMMAND_NAME, "Rewrite your message in a clearer, more professional form",
                CommandVisibility.GUILD);

        this.rewriteMsgService = rewriteMsgService;

        logger.debug("Initializing RewriteMsgCommand with ChatGptService and HelpSystemHelper");

        final OptionData toneOption = new OptionData(OptionType.STRING, TONE_OPTION,
                "The tone/style for the rewritten message (default: "
                        + RewriteMsgTone.CLEAR.getDisplayName() + ")",
                false);

        logger.debug("Adding tone choices to command options");
        Arrays.stream(RewriteMsgTone.values()).forEach(tone -> {
            toneOption.addChoice(tone.getDisplayName(), tone.name());
            logger.debug("Added tone choice: {} ({})", tone.getDisplayName(), tone.name());
        });

        final OptionData messageOption =
                new OptionData(OptionType.STRING, MESSAGE_OPTION, "The message you want to rewrite",
                        true)
                    .setMinLength(MIN_MESSAGE_LENGTH)
                    .setMaxLength(MAX_MESSAGE_LENGTH);

        logger.debug("Configured message option: min={}, max={}", MIN_MESSAGE_LENGTH,
                MAX_MESSAGE_LENGTH);

        super.getData().addOptions(messageOption, toneOption);
        logger.debug("RewriteMsgCommand initialization complete");
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        logger.debug("onSlashCommand method invoked");
        event.deferReply(true).queue();
        logger.debug("Reply deferred as ephemeral");

        final String userId = event.getUser().getId();

        logger.info("Rewrite command triggered by user: {}", userId);

        final String userMessage =
                this.rewriteMsgService.validateMsg(event.getOption(MESSAGE_OPTION), userId);

        final RewriteMsgTone tone =
                this.rewriteMsgService.parseTone(event.getOption(TONE_OPTION), userId);

        final Optional<String> rewrittenMessage =
                this.rewriteMsgService.rewrite(userMessage, tone, userId);

        final Optional<MessageEmbed> responseEmbed =
                this.rewriteMsgService.buildResponse(userMessage, rewrittenMessage.orElse(null),
                        tone, userId, event.getJDA().getSelfUser());

        logger.debug("Sending embed response to user: {}", userId);

        if (responseEmbed.isPresent()) {
            event.getHook()
                .sendMessageEmbeds(responseEmbed.get())
                .queue(_ -> logger.info("Rewrite response sent successfully to user: {}", userId),
                        error -> logger.error("Failed to send rewrite response to user: {}", userId,
                                error));
        } else {
            logger.error("Failed to build response embed for user: {}", userId);
            event.getHook()
                .sendMessage(
                        "An error occurred while processing your request. Please try again later.")
                .queue();
        }
    }
}
