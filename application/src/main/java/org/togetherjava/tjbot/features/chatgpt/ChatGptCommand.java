package org.togetherjava.tjbot.features.chatgpt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.help.HelpSystemHelper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * The implemented command is {@code /chatgpt}, which allows users to ask ChatGPT a question, upon
 * which it will respond with an AI generated answer.
 */
public final class ChatGptCommand extends SlashCommandAdapter {
    public static final String COMMAND_NAME = "chatgpt";
    private static final String QUESTION_INPUT = "question";
    private static final int MAX_MESSAGE_INPUT_LENGTH = 200;
    private static final int MIN_MESSAGE_INPUT_LENGTH = 4;
    private static final Duration COMMAND_COOLDOWN = Duration.of(10, ChronoUnit.SECONDS);
    private final ChatGptService chatGptService;
    private final HelpSystemHelper helper;

    private final Cache<String, Instant> userIdToAskedAtCache =
            Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(COMMAND_COOLDOWN).build();

    /**
     * Creates an instance of the chatgpt command.
     *
     * @param chatGptService ChatGptService - Needed to make calls to ChatGPT API
     * @param helper HelpSystemHelper - Needed to generate response embed for prompt
     */
    public ChatGptCommand(ChatGptService chatGptService, HelpSystemHelper helper) {
        super(COMMAND_NAME, "Ask the ChatGPT AI a question!", CommandVisibility.GUILD);

        this.chatGptService = chatGptService;
        this.helper = helper;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Instant previousAskTime = userIdToAskedAtCache.getIfPresent(event.getMember().getId());
        if (previousAskTime != null) {
            long timeRemainingUntilNextAsk =
                    COMMAND_COOLDOWN.minus(Duration.between(previousAskTime, Instant.now()))
                        .toSeconds();

            event
                .reply("Sorry, you need to wait another " + timeRemainingUntilNextAsk
                        + " second(s) before asking chatGPT another question.")
                .setEphemeral(true)
                .queue();
            return;
        }

        TextInput body = TextInput
            .create(QUESTION_INPUT, "Ask ChatGPT a question or get help with code",
                    TextInputStyle.PARAGRAPH)
            .setPlaceholder("Put your question for ChatGPT here")
            .setRequiredRange(MIN_MESSAGE_INPUT_LENGTH, MAX_MESSAGE_INPUT_LENGTH)
            .build();

        Modal modal = Modal.create(generateComponentId(), "ChatGPT").addActionRow(body).build();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        event.deferReply().queue();

        String context = "";
        String question = event.getValue(QUESTION_INPUT).getAsString();

        Optional<String> optional = chatGptService.ask(question, context);
        if (optional.isPresent()) {
            userIdToAskedAtCache.put(event.getMember().getId(), Instant.now());
        }

        String errorResponse = """
                    An error has occurred while trying to communicate with ChatGPT.
                    Please try again later.
                """;

        String response = optional.orElse(errorResponse);
        SelfUser selfUser = event.getJDA().getSelfUser();

        MessageEmbed responseEmbed = helper.generateGptResponseEmbed(response, selfUser, question);

        event.getHook().sendMessageEmbeds(responseEmbed).queue();
    }
}
