package org.togetherjava.tjbot.features.chaptgpt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

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
    private static final String QUESTION_INPUT = "question";
    private static final int MAX_MESSAGE_INPUT_LENGTH = 200;
    private static final int MIN_MESSAGE_INPUT_LENGTH = 4;
    private static final Duration CACHE_DURATION = Duration.of(10, ChronoUnit.SECONDS);
    private final ChatGptService chatGptService;

    private final Cache<String, Instant> userIdToAskedAtCache =
            Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(CACHE_DURATION).build();

    /**
     * Creates an instance of the chatgpt command.
     *
     * @param chatGptService ChatGptService - Needed to make calls to ChatGPT API
     */
    public ChatGptCommand(ChatGptService chatGptService) {
        super("chatgpt", "Ask the ChatGPT AI a question!", CommandVisibility.GUILD);

        this.chatGptService = chatGptService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Instant previousAskTime = userIdToAskedAtCache.getIfPresent(event.getMember().getId());
        if (previousAskTime != null) {
            long timeRemainingUntilNextAsk = CACHE_DURATION.getSeconds()
                    - Duration.between(previousAskTime, Instant.now()).get(ChronoUnit.SECONDS);

            event
                .reply("Sorry, you need to wait another " + timeRemainingUntilNextAsk
                        + " second(s) before asking chatGPT another question.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (event.getInteraction().getOptions().isEmpty()) {
            TextInput body = TextInput
                .create(QUESTION_INPUT, "Ask ChatGPT a question or get help with code",
                        TextInputStyle.PARAGRAPH)
                .setPlaceholder("Put your question for ChatGPT here")
                .setRequiredRange(MIN_MESSAGE_INPUT_LENGTH, MAX_MESSAGE_INPUT_LENGTH)
                .build();

            Modal modal = Modal.create(generateComponentId(), "ChatGPT").addActionRow(body).build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        event.deferReply().queue();

        Optional<String> optional =
                chatGptService.ask(event.getValue(QUESTION_INPUT).getAsString());
        if (optional.isPresent()) {
            userIdToAskedAtCache.put(event.getMember().getId(), Instant.now());
        }

        String response = optional.orElse(
                "An error has occurred while trying to communicate with ChatGPT. Please try again later");
        event.getHook().sendMessage(response).queue();
    }
}
